package ru.x5.markable.dev.analytics.gitlab.service.impl;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.x5.markable.dev.analytics.gitlab.config.GitProperties;
import ru.x5.markable.dev.analytics.gitlab.client.GitClient;
import ru.x5.markable.dev.analytics.gitlab.model.AuthorAggregate;
import ru.x5.markable.dev.analytics.gitlab.model.CommitDetail;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.DailyAuthorStats;
import ru.x5.markable.dev.analytics.gitlab.persistence.repository.DailyAuthorStatsRepository;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.AuthorSummaryDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.AuthorWeeklySummaryDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.DailyCommitStatsDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.DailyUserStatsDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.PeriodSummaryDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.WeeklyCommitStatsDto;
import ru.x5.markable.dev.analytics.gitlab.service.CommitDetailsService;
import ru.x5.markable.dev.analytics.gitlab.service.DailyStatsService;
import ru.x5.markable.dev.analytics.gitlab.service.ExportTrackerService;
import ru.x5.markable.dev.analytics.gitlab.utill.CommitMessageParser;

@Service
@Log4j2
@RequiredArgsConstructor
public class DailyStatsServiceImpl implements DailyStatsService {

    private final GitClient gitClient;
    private final GitProperties gitProperties;
    private final DailyAuthorStatsRepository dailyStatsRepository;
    private final Executor analysisExecutor;
    private final ExportTrackerService exportTrackerService;
    private final CommitDetailsService commitDetailsService;

    private static final LocalDateTime DEFAULT_START_DATE = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Запускается каждый день в 01:00 Собирает статистику с даты последней выгрузки до текущего момента
     */
    @Override
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void collectDailyStats() {
        log.info("Starting daily stats collection...");

        LocalDateTime lastExport = exportTrackerService.getLastExportTime()
                .orElse(DEFAULT_START_DATE);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startFrom = lastExport.plusSeconds(1);

        if (startFrom.isAfter(now)) {
            log.info("All stats are up to date. Last export: {}", lastExport);
            return;
        }

        log.info("Collecting stats from {} to {}", startFrom, now);
        collectStatsForPeriod(startFrom, now);
    }

    /**
     * Собрать статистику за период с учетом времени
     */
    @Override
    @Transactional
    public void collectStatsForPeriod(LocalDateTime start, LocalDateTime end) {
        log.info("Starting collection for period: {} - {}", start, end);

        try {
            // Собираем коммиты с привязкой к репозиториям
            Map<String, List<CommitDetail>> commitsByRepo = collectCommitDetailsWithRepo(start, end);

            // Для глобальной статистики (без разбивки по репозиториям)
            Map<LocalDate, Map<String, AuthorAggregate>> globalDailyStats = new HashMap<>();

            // Сохраняем статистику по каждому репозиторию отдельно
            for (Map.Entry<String, List<CommitDetail>> entry : commitsByRepo.entrySet()) {
                String repoName = entry.getKey();
                List<CommitDetail> repoCommits = entry.getValue();

                Map<LocalDate, Map<String, AuthorAggregate>> dailyStats = groupCommitsByDay(repoCommits);

                // Сохраняем для конкретного репозитория (нужно для профиля пользователя)
                saveDailyStatsForRepo(dailyStats, repoName);

//                // Объединяем в глобальную статистику
//                for (Map.Entry<LocalDate, Map<String, AuthorAggregate>> dayEntry : dailyStats.entrySet()) {
//                    LocalDate date = dayEntry.getKey();
//                    Map<String, AuthorAggregate> dayStats = dayEntry.getValue();
//
//                    globalDailyStats.computeIfAbsent(date, k -> new HashMap<>());
//                    Map<String, AuthorAggregate> globalDayStats = globalDailyStats.get(date);
//
//                    for (Map.Entry<String, AuthorAggregate> statEntry : dayStats.entrySet()) {
//                        globalDayStats.merge(statEntry.getKey(), statEntry.getValue(), AuthorAggregate::merge);
//                    }
//                }
            }

//            // Сохраняем глобальную статистику (для общей аналитики)
//            if (!globalDailyStats.isEmpty()) {
//                saveDailyStatsForRepo(globalDailyStats, "ALL_REPOS");
//            }

            exportTrackerService.markExportSuccess(end);
            log.info("Successfully collected stats from {} to {}", start, end);

        } catch (Exception e) {
            exportTrackerService.markExportFailed(start, end, e.getMessage());
            log.error("Failed to collect stats from {} to {}", start, end, e);
        }
    }

    @Override
    public PeriodSummaryDto getPeriodSummary() {
        log.info("Fetching summary for all available data");

        List<DailyAuthorStats> allStats = dailyStatsRepository.findAll();

        if (allStats.isEmpty()) {
            return createEmptyPeriodSummary();
        }

        LocalDate minDate = findMinDate(allStats);
        LocalDate maxDate = findMaxDate(allStats);

        PeriodSummaryAggregation aggregation = aggregatePeriodStats(allStats);
        Map<String, AuthorSummaryDto> topAuthors = extractTopAuthors(aggregation.authorMap());

        return PeriodSummaryDto.builder()
                .totalCommits(aggregation.totalCommits())
                .totalMergeCommits(aggregation.totalMergeCommits())
                .totalAddedLines(aggregation.totalAdded())
                .totalDeletedLines(aggregation.totalDeleted())
                .totalTestAddedLines(aggregation.totalTestAdded())
                .uniqueAuthors(aggregation.authorMap().size())
                .topAuthors(topAuthors)
                .dateFrom(minDate)
                .dateTo(maxDate)
                .build();
    }

    /**
     * Создаёт пустую сводку за период.
     * Используется когда в базе нет статистических данных.
     *
     * @return пустой DTO с нулевыми значениями
     */
    private PeriodSummaryDto createEmptyPeriodSummary() {
        return PeriodSummaryDto.builder()
                .totalCommits(0)
                .totalMergeCommits(0)
                .totalAddedLines(0)
                .totalDeletedLines(0)
                .totalTestAddedLines(0)
                .uniqueAuthors(0)
                .topAuthors(new LinkedHashMap<>())
                .build();
    }

    /**
     * Находит минимальную дату в списке статистики.
     *
     * @param stats список статистических записей
     * @return минимальная дата или null если список пуст
     */
    private LocalDate findMinDate(List<DailyAuthorStats> stats) {
        return stats.stream()
                .map(DailyAuthorStats::getDate)
                .min(LocalDate::compareTo)
                .orElse(null);
    }

    /**
     * Находит максимальную дату в списке статистики.
     *
     * @param stats список статистических записей
     * @return максимальная дата или null если список пуст
     */
    private LocalDate findMaxDate(List<DailyAuthorStats> stats) {
        return stats.stream()
                .map(DailyAuthorStats::getDate)
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    /**
     * Агрегирует статистику за период, суммируя показатели по всем авторам.
     *
     * @param stats список статистических записей
     * @return объект агрегации с суммарными показателями и картой авторов
     */
    private PeriodSummaryAggregation aggregatePeriodStats(List<DailyAuthorStats> stats) {
        Map<String, AuthorSummaryDto> authorMap = new HashMap<>();
        long totalCommits = 0;
        long totalMergeCommits = 0;
        long totalAdded = 0;
        long totalDeleted = 0;
        long totalTestAdded = 0;

        for (DailyAuthorStats stat : stats) {
            totalCommits += stat.getCommits();
            totalMergeCommits += Math.max(0, stat.getMergeCommits());
            totalAdded += stat.getAddedLines();
            totalDeleted += stat.getDeletedLines();
            totalTestAdded += Math.max(0, stat.getTestAddedLines());

            authorMap.compute(stat.getEmail(), (email, summary) -> {
                if (summary == null) {
                    summary = AuthorSummaryDto.builder()
                            .email(email)
                            .commits(0)
                            .addedLines(0)
                            .deletedLines(0)
                            .build();
                }
                summary.setCommits(summary.getCommits() + stat.getCommits());
                summary.setAddedLines(summary.getAddedLines() + stat.getAddedLines());
                summary.setDeletedLines(summary.getDeletedLines() + stat.getDeletedLines());
                return summary;
            });
        }

        return new PeriodSummaryAggregation(totalCommits, totalMergeCommits, totalAdded,
                totalDeleted, totalTestAdded, authorMap);
    }

    /**
     * Извлекает топ-10 авторов по количеству коммитов.
     *
     * @param authorMap карта всех авторов с их статистикой
     * @return упорядоченная карта топ-10 авторов
     */
    private Map<String, AuthorSummaryDto> extractTopAuthors(Map<String, AuthorSummaryDto> authorMap) {
        return authorMap.entrySet().stream()
                .sorted(Map.Entry.<String, AuthorSummaryDto>comparingByValue(
                        Comparator.comparingLong(AuthorSummaryDto::getCommits).reversed()))
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    /**
     * Вспомогательный record для хранения агрегированной статистики за период.
     */
    private record PeriodSummaryAggregation(
            long totalCommits,
            long totalMergeCommits,
            long totalAdded,
            long totalDeleted,
            long totalTestAdded,
            Map<String, AuthorSummaryDto> authorMap
    ) {}

    @Override
    public List<WeeklyCommitStatsDto> getWeeklyCommits() {
        log.info("Fetching weekly commits statistics");

        List<DailyAuthorStats> allStats = dailyStatsRepository.findAll();

        if (allStats.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Integer, Map<String, List<DailyAuthorStats>>> weeklyData = groupStatsByWeek(allStats);
        List<WeeklyCommitStatsDto> result = buildWeeklyStats(weeklyData);

        log.info("Found {} weeks of data", result.size());
        return result;
    }

    /**
     * Группирует статистику по неделям и авторам.
     * Ключ карты - уникальный идентификатор недели (год * 100 + номер недели).
     *
     * @param stats список статистических записей
     * @return карта, сгруппированная по неделям и авторам
     */
    private Map<Integer, Map<String, List<DailyAuthorStats>>> groupStatsByWeek(List<DailyAuthorStats> stats) {
        Map<Integer, Map<String, List<DailyAuthorStats>>> weeklyData = new java.util.TreeMap<>();

        for (DailyAuthorStats stat : stats) {
            LocalDate date = stat.getDate();
            int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
            int year = date.getYear();
            int weekKey = year * 100 + week;

            weeklyData.computeIfAbsent(weekKey, k -> new HashMap<>());
            weeklyData.get(weekKey).computeIfAbsent(stat.getEmail(), k -> new ArrayList<>())
                    .add(stat);
        }

        return weeklyData;
    }

    /**
     * Строит DTO недельной статистики из сгруппированных данных.
     *
     * @param weeklyData карта, сгруппированная по неделям и авторам
     * @return список DTO недельной статистики, отсортированный по дате начала недели
     */
    private List<WeeklyCommitStatsDto> buildWeeklyStats(Map<Integer, Map<String, List<DailyAuthorStats>>> weeklyData) {
        List<WeeklyCommitStatsDto> result = new ArrayList<>();

        for (Map.Entry<Integer, Map<String, List<DailyAuthorStats>>> entry : weeklyData.entrySet()) {
            int weekKey = entry.getKey();
            int year = weekKey / 100;
            int week = weekKey % 100;
            Map<String, List<DailyAuthorStats>> userData = entry.getValue();

            LocalDate weekStart = calculateWeekStart(year, week);
            LocalDate weekEnd = weekStart.plusDays(6);

            WeeklyAggregation aggregation = aggregateWeeklyStats(userData);
            Map<String, AuthorWeeklySummaryDto> topAuthors = sortAuthorsByCommits(aggregation.authors());

            result.add(WeeklyCommitStatsDto.builder()
                    .weekNumber(week)
                    .weekStart(weekStart)
                    .weekEnd(weekEnd)
                    .totalCommits(aggregation.totalCommits())
                    .totalMergeCommits(aggregation.totalMergeCommits())
                    .totalAddedLines(aggregation.totalAdded())
                    .totalDeletedLines(aggregation.totalDeleted())
                    .totalTestAddedLines(aggregation.totalTestAdded())
                    .uniqueAuthors(aggregation.authors().size())
                    .topAuthors(topAuthors)
                    .build());
        }

        result.sort(Comparator.comparing(WeeklyCommitStatsDto::getWeekStart));
        return result;
    }

    /**
     * Вычисляет дату начала недели по ISO стандарту.
     *
     * @param year год
     * @param week номер недели (1-53)
     * @return дата начала недели (понедельник)
     */
    private LocalDate calculateWeekStart(int year, int week) {
        return LocalDate.of(year, 1, 1)
                .with(WeekFields.ISO.weekOfWeekBasedYear(), week)
                .with(WeekFields.ISO.dayOfWeek(), 1);
    }

    /**
     * Агрегирует статистику за неделю для всех авторов.
     *
     * @param userData карта авторов с их статистическими записями за неделю
     * @return объект агрегации с суммарными показателями и картой авторов
     */
    private WeeklyAggregation aggregateWeeklyStats(Map<String, List<DailyAuthorStats>> userData) {
        long totalCommits = 0;
        long totalMergeCommits = 0;
        long totalAdded = 0;
        long totalDeleted = 0;
        long totalTestAdded = 0;
        Map<String, AuthorWeeklySummaryDto> authors = new HashMap<>();

        for (Map.Entry<String, List<DailyAuthorStats>> userEntry : userData.entrySet()) {
            String email = userEntry.getKey();
            long userCommits = 0;
            long userMergeCommits = 0;
            long userAdded = 0;
            long userDeleted = 0;
            long userTestAdded = 0;

            for (DailyAuthorStats stat : userEntry.getValue()) {
                userCommits += stat.getCommits();
                userMergeCommits += Math.max(0, stat.getMergeCommits());
                userAdded += stat.getAddedLines();
                userDeleted += stat.getDeletedLines();
                userTestAdded += Math.max(0, stat.getTestAddedLines());

                totalCommits += stat.getCommits();
                totalMergeCommits += Math.max(0, stat.getMergeCommits());
                totalAdded += stat.getAddedLines();
                totalDeleted += stat.getDeletedLines();
                totalTestAdded += Math.max(0, stat.getTestAddedLines());
            }

            authors.put(email, AuthorWeeklySummaryDto.builder()
                    .email(email)
                    .commits(userCommits)
                    .mergeCommits(userMergeCommits)
                    .addedLines(userAdded)
                    .deletedLines(userDeleted)
                    .testAddedLines(userTestAdded)
                    .build());
        }

        return new WeeklyAggregation(totalCommits, totalMergeCommits, totalAdded,
                totalDeleted, totalTestAdded, authors);
    }

    /**
     * Сортирует авторов по количеству коммитов в убывающем порядке.
     *
     * @param authors карта авторов с их статистикой
     * @return упорядоченная карта авторов
     */
    private Map<String, AuthorWeeklySummaryDto> sortAuthorsByCommits(Map<String, AuthorWeeklySummaryDto> authors) {
        return authors.entrySet().stream()
                .sorted(Map.Entry.<String, AuthorWeeklySummaryDto>comparingByValue(
                        Comparator.comparingLong(AuthorWeeklySummaryDto::getCommits).reversed()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    /**
     * Вспомогательный record для хранения агрегированной недельной статистики.
     */
    private record WeeklyAggregation(
            long totalCommits,
            long totalMergeCommits,
            long totalAdded,
            long totalDeleted,
            long totalTestAdded,
            Map<String, AuthorWeeklySummaryDto> authors
    ) {}

    @Override
    public List<DailyCommitStatsDto> getAllDailyCommits() {
        log.info("Fetching all daily commits");

        List<DailyAuthorStats> allStats = dailyStatsRepository.findAll();
        Map<LocalDate, DailyCommitStatsDto> dailyMap = aggregateDailyStats(allStats);

        return dailyMap.values().stream()
                .sorted(Comparator.comparing(DailyCommitStatsDto::getDate))
                .toList();
    }

    /**
     * Агрегирует статистику по дням, суммируя показатели всех авторов за каждый день.
     *
     * @param stats список статистических записей
     * @return карта, сгруппированная по датам с агрегированной статистикой
     */
    private Map<LocalDate, DailyCommitStatsDto> aggregateDailyStats(List<DailyAuthorStats> stats) {
        Map<LocalDate, DailyCommitStatsDto> dailyMap = new LinkedHashMap<>();

        for (DailyAuthorStats stat : stats) {
            dailyMap.compute(stat.getDate(), (date, dto) -> {
                if (dto == null) {
                    dto = DailyCommitStatsDto.builder()
                            .date(date)
                            .totalCommits(0)
                            .totalMergeCommits(0)
                            .totalAddedLines(0)
                            .totalDeletedLines(0)
                            .totalTestAddedLines(0)
                            .build();
                }
                dto.setTotalCommits(dto.getTotalCommits() + stat.getCommits());
                dto.setTotalMergeCommits(dto.getTotalMergeCommits() + Math.max(0, stat.getMergeCommits()));
                dto.setTotalAddedLines(dto.getTotalAddedLines() + stat.getAddedLines());
                dto.setTotalDeletedLines(dto.getTotalDeletedLines() + stat.getDeletedLines());
                dto.setTotalTestAddedLines(dto.getTotalTestAddedLines() + Math.max(0, stat.getTestAddedLines()));
                return dto;
            });
        }

        return dailyMap;
    }

    @Override
    public List<DailyUserStatsDto> getAllDailyUserStats() {
        log.info("Fetching all daily user stats");

        return dailyStatsRepository.findAll().stream()
                .map(stat -> DailyUserStatsDto.builder()
                        .date(stat.getDate())
                        .email(stat.getEmail())
                        .commits(stat.getCommits())
                        .mergeCommits(Math.max(0, stat.getMergeCommits()))
                        .addedLines(stat.getAddedLines())
                        .deletedLines(stat.getDeletedLines())
                        .testAddedLines(Math.max(0, stat.getTestAddedLines()))
                        .build())
                .sorted(Comparator.comparing(DailyUserStatsDto::getDate)
                        .thenComparing(DailyUserStatsDto::getEmail))
                .toList();
    }

    /**
     * Собирает коммиты из всех репозиториев с привязкой к имени репозитория.
     * Использует параллельную обработку для ускорения сбора данных.
     *
     * @param start начало периода сбора статистики
     * @param end конец периода сбора статистики
     * @return карта, где ключ - имя репозитория, значение - список деталей коммитов
     */
    private Map<String, List<CommitDetail>> collectCommitDetailsWithRepo(LocalDateTime start, LocalDateTime end) {
        log.info("Collecting commit details between {} and {}", start, end);

        Map<String, List<CommitDetail>> commitsByRepo = new ConcurrentHashMap<>();

        List<CompletableFuture<Void>> futures = gitProperties.getRepositories()
                .stream()
                .map(repo -> CompletableFuture.runAsync(
                        () -> {
                            String repoName = extractRepoName(repo);
                            List<CommitDetail> repoCommits = collectForRepositoryWithDetails(repo, start, end);
                            commitsByRepo.put(repoName, repoCommits);
                        },
                        analysisExecutor))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        return commitsByRepo;
    }

    /**
     * Собирает коммиты из указанного репозитория за заданный период.
     * Фильтрует коммиты по дате и сохраняет детали в базу данных.
     *
     * @param repoUrl URL репозитория
     * @param start начало периода
     * @param end конец периода
     * @return список деталей коммитов, попавших в указанный период
     */
    private List<CommitDetail> collectForRepositoryWithDetails(String repoUrl,
            LocalDateTime start,
            LocalDateTime end) {
        String repoName = extractRepoName(repoUrl);
        List<CommitDetail> repoCommits = new ArrayList<>();

        try {
            log.debug("Processing repository [{}] for period {} - {}", repoName, start, end);

            Path repoPath = gitClient.prepareRepository(repoUrl);
            List<String> lines = gitClient.collectStats(repoPath, start, end);

            if (lines.isEmpty()) {
                log.debug("No commits found for {} in this period", repoName);
                return repoCommits;
            }

            List<CommitDetail> allCommits = parseGitOutputWithDates(lines);

            for (CommitDetail commit : allCommits) {
                LocalDateTime commitDate = commit.getCommitDate();

                if (commitDate != null && !commitDate.isBefore(start) && !commitDate.isAfter(end)) {
                    commit.setRepoName(repoName);
                    repoCommits.add(commit);
                } else {
                    log.debug("Filtered out commit from {} (period: {} - {})",
                            commitDate, start, end);
                }
            }

            if (CollectionUtils.isNotEmpty(repoCommits)) {
                commitDetailsService.saveCommitDetails(repoCommits);
                log.debug("Saved {} commit details for repo {}", repoCommits.size(), repoName);
            }

            log.debug("Repository [{}] processed, kept {} of {} commits within period",
                    repoName, repoCommits.size(), allCommits.size());

        } catch (Exception e) {
            log.error("Failed to process repository {} for period {} - {}", repoName, start, end, e);
        }

        return repoCommits;
    }

    /**
     * Парсит вывод git log с датами.
     * Формат строки с коммитом: hash|email|parent|date|message
     * Формат строки с numstat: added\tdeleted\tfilename
     *
     * @param lines список строк вывода git log
     * @return список деталей коммитов
     */
    private List<CommitDetail> parseGitOutputWithDates(List<String> lines) {
        List<CommitDetail> commits = new ArrayList<>();
        CommitDetail currentCommit = null;

        for (String rawLine : lines) {
            if (rawLine == null || rawLine.trim().isEmpty()) {
                continue;
            }

            String line = rawLine.trim();

            if (isCommitHeaderLine(line)) {
                currentCommit = parseCommitHeaderLine(line);
                if (currentCommit != null) {
                    commits.add(currentCommit);
                }
            } else if (isNumstatLine(line) && currentCommit != null) {
                processNumstatLine(line, currentCommit);
            }
        }

        log.info("Parsed {} commits from git output", commits.size());
        return commits;
    }

    /**
     * Проверяет, является ли строка заголовком коммита.
     * Заголовок содержит email (@) и не содержит табуляции.
     *
     * @param line строка для проверки
     * @return true если это заголовок коммита
     */
    private boolean isCommitHeaderLine(String line) {
        return !line.contains("\t") && line.contains("@");
    }

    /**
     * Проверяет, является ли строка numstat.
     * Numstat содержит табуляцию.
     *
     * @param line строка для проверки
     * @return true если это строка numstat
     */
    private boolean isNumstatLine(String line) {
        return line.contains("\t");
    }

    /**
     * Парсит строку заголовка коммита и создаёт объект CommitDetail.
     * Формат: hash|email|parent|date|message
     *
     * @param line строка заголовка коммита
     * @return объект CommitDetail или null при ошибке парсинга
     */
    private CommitDetail parseCommitHeaderLine(String line) {
        String[] parts = line.split("\\|");
        if (parts.length < 5) {
            return null;
        }

        String hash = parts[0].trim();
        String email = parts[1].trim().toLowerCase();
        String parent = parts[2].trim();
        String dateStr = parts[3].trim();
        String message = parts[4].trim();

        boolean isMerge = parent.contains(" ");

        try {
            LocalDateTime commitDateUTC = convertToUtc(dateStr);
            String taskNumber = CommitMessageParser.extractTaskNumber(message);

            CommitDetail commit = new CommitDetail();
            commit.setHash(hash);
            commit.setEmail(email);
            commit.setCommitDate(commitDateUTC);
            commit.setMerge(isMerge);
            commit.setAdded(0);
            commit.setDeleted(0);
            commit.setTestAdded(0);
            commit.setCommitMessage(message);
            commit.setTaskNumber(taskNumber);

            return commit;

        } catch (DateTimeParseException e) {
            log.warn("Failed to parse date: '{}' for email: {}", dateStr, email);
            return null;
        }
    }

    /**
     * Конвертирует строку даты в LocalDateTime в UTC.
     *
     * @param dateStr строка даты в формате ISO_OFFSET_DATE_TIME
     * @return LocalDateTime в UTC
     * @throws DateTimeParseException при ошибке парсинга
     */
    private LocalDateTime convertToUtc(String dateStr) throws DateTimeParseException {
        ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return zonedDateTime.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * Обрабатывает строку numstat и обновляет статистику коммита.
     * Формат: added\tdeleted\tfilename
     *
     * @param line строка numstat
     * @param commit объект коммита для обновления
     */
    private void processNumstatLine(String line, CommitDetail commit) {
        String[] parts = line.split("\t");
        if (parts.length < 3) {
            return;
        }

        if (parts[0].equals("-") || parts[1].equals("-")) {
            return;
        }

        try {
            long added = Long.parseLong(parts[0]);
            long deleted = Long.parseLong(parts[1]);
            String fileName = parts[2];
            boolean isTest = isTestFile(fileName);

            commit.setAdded(commit.getAdded() + added);
            commit.setDeleted(commit.getDeleted() + deleted);
            if (isTest) {
                commit.setTestAdded(commit.getTestAdded() + added);
            }
        } catch (NumberFormatException e) {
            log.debug("Failed to parse numstat: {}", line);
        }
    }

    /**
     * Группирует коммиты по дням и пользователям.
     * Пропускает коммиты старше минимально допустимой даты (01.01.2026).
     *
     * @param commits список деталей коммитов
     * @return карта, сгруппированная по датам и email авторов
     */
    private Map<LocalDate, Map<String, AuthorAggregate>> groupCommitsByDay(List<CommitDetail> commits) {
        Map<LocalDate, Map<String, AuthorAggregate>> dailyStats = new HashMap<>();

        LocalDate minAllowedDate = LocalDate.of(2026, 1, 1);

        for (CommitDetail commit : commits) {
            LocalDate day = commit.getCommitDate().toLocalDate();

            if (day.isBefore(minAllowedDate)) {
                log.debug("Skipping old commit from: {}", day);
                continue;
            }

            Map<String, AuthorAggregate> dayStats = dailyStats.computeIfAbsent(day, k -> new HashMap<>());
            
            dayStats.merge(commit.getEmail(),
                    new AuthorAggregate(commit.getEmail())
                            .addCommit(commit.isMerge())
                            .addLines(commit.getAdded(), commit.getDeleted(), commit.getTestAdded() > 0),
                    (existing, newAgg) -> existing.merge(newAgg));
        }

        return dailyStats;
    }

    /**
     * Сохраняет статистику по дням для конкретного репозитория.
     * Обновляет существующие записи или создаёт новые.
     *
     * @param dailyStats карта сгруппированной статистики по дням и авторам
     * @param repoName имя репозитория
     */
    private void saveDailyStatsForRepo(Map<LocalDate, Map<String, AuthorAggregate>> dailyStats, String repoName) {
        List<DailyAuthorStats> newRecords = new ArrayList<>();
        List<DailyAuthorStats> updateRecords = new ArrayList<>();

        for (Map.Entry<LocalDate, Map<String, AuthorAggregate>> dayEntry : dailyStats.entrySet()) {
            LocalDate date = dayEntry.getKey();
            Map<String, AuthorAggregate> dayStats = dayEntry.getValue();

            for (Map.Entry<String, AuthorAggregate> statEntry : dayStats.entrySet()) {
                String email = statEntry.getKey();
                AuthorAggregate stat = statEntry.getValue();

                dailyStatsRepository.findByEmailAndDateAndRepositoryName(email, date, repoName)
                        .ifPresentOrElse(existing -> {
                            existing.setMergeCommits(stat.mergeCommits());
                            existing.setCommits(stat.commits());
                            existing.setAddedLines(stat.added());
                            existing.setDeletedLines(stat.deleted());
                            existing.setTestAddedLines(stat.testAdded());
                            existing.setLastUpdated(LocalDateTime.now());
                            updateRecords.add(existing);
                        }, () -> {
                            DailyAuthorStats newStats = DailyAuthorStats.builder()
                                    .email(email)
                                    .date(date)
                                    .repositoryName(repoName)
                                    .mergeCommits(stat.mergeCommits())
                                    .commits(stat.commits())
                                    .addedLines(stat.added())
                                    .deletedLines(stat.deleted())
                                    .testAddedLines(stat.testAdded())
                                    .lastUpdated(LocalDateTime.now())
                                    .build();
                            newRecords.add(newStats);
                        });
            }
        }

        if (CollectionUtils.isNotEmpty(newRecords)) {
            dailyStatsRepository.saveAll(newRecords);
            log.info("Saved {} new daily stats records for repo {}", newRecords.size(), repoName);
        }

        if (CollectionUtils.isNotEmpty(updateRecords)) {
            dailyStatsRepository.saveAll(updateRecords);
            log.info("Updated {} existing daily stats records for repo {}", updateRecords.size(), repoName);
        }
    }

    /**
     * Проверяет, является ли файл тестовым.
     * Тестовым считается файл, содержащий "/test/" в пути или
     * оканчивающийся на "test.java" или "tests.java".
     *
     * @param fileName имя файла
     * @return true если файл тестовый, иначе false
     */
    private boolean isTestFile(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase();
        return lower.contains("/test/") ||
                lower.endsWith("test.java") ||
                lower.endsWith("tests.java");
    }

    /**
     * Извлекает имя репозитория из URL.
     * Удаляет путь и расширение .git.
     *
     * @param repoUrl URL репозитория
     * @return имя репозитория
     */
    private String extractRepoName(String repoUrl) {
        return repoUrl.substring(repoUrl.lastIndexOf("/") + 1).replace(".git", "");
    }
}
