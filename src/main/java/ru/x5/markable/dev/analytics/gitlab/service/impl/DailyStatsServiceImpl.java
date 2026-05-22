package ru.x5.markable.dev.analytics.gitlab.service.impl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.x5.markable.dev.analytics.gitlab.config.GitProperties;
import ru.x5.markable.dev.analytics.gitlab.model.CommitDetail;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.DailyAuthorStats;
import ru.x5.markable.dev.analytics.gitlab.persistence.repository.DailyAuthorStatsRepository;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.AuthorSummaryDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.DailyCommitStatsDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.DailyUserStatsDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.PeriodSummaryDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.WeeklyCommitStatsDto;
import ru.x5.markable.dev.analytics.gitlab.service.DailyStatsService;
import ru.x5.markable.dev.analytics.gitlab.service.ExportTrackerService;
import ru.x5.markable.dev.analytics.gitlab.service.impl.helper.GitCommitCollector;
import ru.x5.markable.dev.analytics.gitlab.service.impl.helper.StatsAggregator;
import ru.x5.markable.dev.analytics.gitlab.service.impl.helper.StatsPersistenceHelper;
import ru.x5.markable.dev.analytics.gitlab.service.impl.helper.WeeklyStatsBuilder;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenCardCollectorService;

import java.time.LocalDate;
import java.util.LinkedHashMap;

/**
 * Сервис для сбора и предоставления ежедневной статистики коммитов.
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Сбор статистики из Git-репозиториев по расписанию</li>
 *   <li>Агрегация данных по дням, неделям и периодам</li>
 *   <li>Предоставление статистики через REST API</li>
 *   <li>Интеграция с Kaiten для сбора карточек задач</li>
 * </ul>
 * 
 * <p>Сервис использует вспомогательные классы для разделения ответственности:</p>
 * <ul>
 *   <li>{@link GitCommitCollector} - сбор и парсинг коммитов из Git</li>
 *   <li>{@link StatsAggregator} - агрегация статистических данных</li>
 *   <li>{@link StatsPersistenceHelper} - сохранение данных в БД</li>
 *   <li>{@link WeeklyStatsBuilder} - построение DTO недельной статистики</li>
 * </ul>
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class DailyStatsServiceImpl implements DailyStatsService {

    private final GitCommitCollector gitCommitCollector;
    private final GitProperties gitProperties;
    private final DailyAuthorStatsRepository dailyStatsRepository;
    private final Executor analysisExecutor;
    private final ExportTrackerService exportTrackerService;
    private final StatsAggregator statsAggregator;
    private final StatsPersistenceHelper statsPersistenceHelper;
    private final WeeklyStatsBuilder weeklyStatsBuilder;
    private final KaitenCardCollectorService kaitenCardCollectorService;

    private static final LocalDateTime DEFAULT_START_DATE = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

    /**
     * Запускается каждый день в 01:00.
     * Собирает статистику с даты последней выгрузки до текущего момента.
     *
     * <p>Без @Transactional — каждый шаг (Git → save → Kaiten) сам управляет своей
     * транзакцией. Иначе ошибка Kaiten в конце откатывала бы уже сохранённые git stats.</p>
     */
    @Override
    @Scheduled(cron = "0 0 1 * * ?")
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
     * Собрать статистику за период с учетом времени.
     *
     * <p>Не транзакционен по верху: каждый шаг (Git, save, Kaiten) пишет в БД самостоятельно.
     * Ошибка Kaiten НЕ откатывает уже сохранённые git stats.</p>
     */
    @Override
    public void collectStatsForPeriod(LocalDateTime start, LocalDateTime end) {
        log.info("Starting collection for period: {} - {}", start, end);

        try {
            Map<String, List<CommitDetail>> commitsByRepo = collectCommitDetailsWithRepo(start, end);

            // Сохранение daily stats параллельно по репозиториям
            List<CompletableFuture<Void>> saveFutures = commitsByRepo.entrySet().stream()
                    .map(entry -> CompletableFuture.runAsync(() -> {
                        String repoName = entry.getKey();
                        Map<LocalDate, Map<String, ru.x5.markable.dev.analytics.gitlab.model.AuthorAggregate>> dailyStats =
                                statsAggregator.groupCommitsByDay(entry.getValue());
                        statsPersistenceHelper.saveDailyStatsForRepo(dailyStats, repoName);
                    }, analysisExecutor))
                    .toList();
            CompletableFuture.allOf(saveFutures.toArray(CompletableFuture[]::new)).join();

            exportTrackerService.markExportSuccess(end);
            log.info("Successfully collected stats from {} to {}", start, end);

        } catch (Exception e) {
            exportTrackerService.markExportFailed(start, end, e.getMessage());
            log.error("Failed to collect stats from {} to {}", start, end, e);
            return;
        }

        // Kaiten — изолирован: его ошибка не должна валить уже сохранённую git-статистику
        try {
            log.info("Starting Kaiten cards collection after successful Git stats collection");
            kaitenCardCollectorService.collectCardsForAllUsers(start);
            log.info("Kaiten cards collection completed successfully");
        } catch (Exception e) {
            log.error("Kaiten cards collection failed (git stats are already saved): {}", e.getMessage());
        }
    }

    /**
     * Получает сводную статистику за весь период.
     *
     * @return DTO с агрегированной статистикой
     */
    @Override
    public PeriodSummaryDto getPeriodSummary() {
        log.info("Fetching summary for all available data");

        List<DailyAuthorStats> allStats = dailyStatsRepository.findAll();

        if (allStats.isEmpty()) {
            return createEmptyPeriodSummary();
        }

        LocalDate minDate = statsAggregator.findMinDate(allStats);
        LocalDate maxDate = statsAggregator.findMaxDate(allStats);

        StatsAggregator.PeriodSummaryAggregation aggregation = statsAggregator.aggregatePeriodStats(allStats);
        Map<String, AuthorSummaryDto> topAuthors = statsAggregator.extractTopAuthors(aggregation.authorMap());

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
     * Получает недельную статистику коммитов.
     *
     * @return список DTO недельной статистики
     */
    @Override
    public List<WeeklyCommitStatsDto> getWeeklyCommits() {
        log.info("Fetching weekly commits statistics");

        List<DailyAuthorStats> allStats = dailyStatsRepository.findAll();

        if (allStats.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Integer, Map<String, List<DailyAuthorStats>>> weeklyData = statsAggregator.groupStatsByWeek(allStats);
        List<WeeklyCommitStatsDto> result = weeklyStatsBuilder.buildWeeklyStats(weeklyData);

        log.info("Found {} weeks of data", result.size());
        return result;
    }

    /**
     * Получает ежедневную статистику коммитов (агрегированную по всем авторам).
     *
     * @return список DTO ежедневной статистики
     */
    @Override
    public List<DailyCommitStatsDto> getAllDailyCommits() {
        log.info("Fetching all daily commits");

        List<DailyAuthorStats> allStats = dailyStatsRepository.findAll();
        Map<LocalDate, DailyCommitStatsDto> dailyMap = statsAggregator.aggregateDailyStats(allStats);

        return dailyMap.values().stream()
                .sorted(Comparator.comparing(DailyCommitStatsDto::getDate))
                .toList();
    }

    /**
     * Получает ежедневную статистику по пользователям.
     *
     * @return список DTO статистики по пользователям
     */
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
                            List<CommitDetail> repoCommits = gitCommitCollector.collectForRepositoryWithDetails(
                                    repo, repoName, start, end);
                            commitsByRepo.put(repoName, repoCommits);
                        },
                        analysisExecutor))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        return commitsByRepo;
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
