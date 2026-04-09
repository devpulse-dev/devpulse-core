package ru.x5.markable.dev.analytics.gitlab.service.impl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import ru.x5.markable.dev.analytics.gitlab.persistence.entity.DailyAuthorStats;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.UnifiedUser;
import ru.x5.markable.dev.analytics.gitlab.persistence.repository.DailyAuthorStatsRepository;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.CommitDetailDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.KaitenCardStatus;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.KaitenCardWithCommitsDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.TaskWithCommitsDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.UserProfileDto;
import ru.x5.markable.dev.analytics.gitlab.service.CommitDetailsService;
import ru.x5.markable.dev.analytics.gitlab.service.UnifiedUserService;
import ru.x5.markable.dev.analytics.gitlab.service.UserProfileService;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenCard;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenCardMemberService;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenCardService;

@Service
@Log4j2
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final DailyAuthorStatsRepository dailyStatsRepository;
    private final CommitDetailsService commitDetailsService;
    private final UnifiedUserService unifiedUserService;
    private final KaitenCardMemberService kaitenCardMemberService;
    private final KaitenCardService kaitenCardService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM");
    private static final Locale RUSSIAN_LOCALE = Locale.forLanguageTag("ru");

    @Override
    public UserProfileDto getUserProfile(String email) {
        return getUserProfile(email, null, null);
    }

    @Override
    public UserProfileDto getUserProfile(String email, LocalDate periodStart, LocalDate periodEnd) {
        log.info("Fetching profile for user: {} with period: {} - {}", email, periodStart, periodEnd);

        Optional<UnifiedUser> unifiedUser = unifiedUserService.findByEmail(email);

        List<DailyAuthorStats> userStats = fetchUserStats(email, periodStart, periodEnd);

        if (userStats.isEmpty()) {
            log.warn("No data found for user: {}", email);
            return null;
        }

        var aggregatedStats = aggregateStats(userStats);
        var activityByDay = calculateActivityByDay(userStats);
        var inactivePeriods = calculateInactivePeriods(userStats);
        var repositories = fetchRepositories(email, periodStart, periodEnd);
        var activityByHour = fetchHourlyActivity(email, periodStart, periodEnd);
        var tasks = fetchTasks(email, periodStart, periodEnd);
        var kaitenCards = fetchKaitenCards(unifiedUser.orElse(null), tasks, periodStart, periodEnd);

        LocalDate firstDate = userStats.get(0).getDate();
        LocalDate lastDate = userStats.get(userStats.size() - 1).getDate();
        long activeDays = activityByDay.values().stream().filter(v -> v != 0).count();
        long totalDays = ChronoUnit.DAYS.between(firstDate, lastDate) + 1;
        double avgCommitsPerDay = (double) aggregatedStats.totalCommits() / activeDays;

        String aiSummary = generateRealSummary(
                email, aggregatedStats.totalCommits(), activeDays, totalDays,
                firstDate, lastDate,
                activityByDay, activityByHour,
                inactivePeriods,
                repositories,
                tasks
        );

        return UserProfileDto.builder()
                .email(email)
                .username(extractUsername(email))
                .joinedDate(firstDate)
                .avatarUrl(unifiedUser.map(UnifiedUser::getAvatarUrl).orElse(null))
                .totalCommits(aggregatedStats.totalCommits())
                .totalMergeCommits(aggregatedStats.totalMergeCommits())
                .totalAddedLines(aggregatedStats.totalAdded())
                .totalDeletedLines(aggregatedStats.totalDeleted())
                .totalTestAddedLines(aggregatedStats.totalTestAdded())
                .activeDays(activeDays)
                .totalDays(totalDays)
                .avgCommitsPerDay(avgCommitsPerDay)
                .periodStart(firstDate)
                .periodEnd(lastDate)
                .activityByDay(activityByDay)
                .activityByHour(activityByHour)
                .repositories(new ArrayList<>(repositories))
                .tasks(tasks)
                .kaitenCards(kaitenCards)
                .inactivePeriods(inactivePeriods)
                .aiSummary(aiSummary)
                .build();
    }

    @Override
    public List<CommitDetailDto> getUserCommits(String email) {
        log.info("Fetching commits for user: {}", email);
        return commitDetailsService.getUserCommits(email);
    }

    private List<DailyAuthorStats> fetchUserStats(String email, LocalDate periodStart, LocalDate periodEnd) {
        return Optional.ofNullable(periodStart)
                .flatMap(start -> Optional.ofNullable(periodEnd)
                        .map(end -> dailyStatsRepository.findByEmailAndDateBetween(email, start, end)))
                .orElseGet(() -> dailyStatsRepository.findByEmailOrderByDateAsc(email));
    }

    private Map<Integer, Long> fetchHourlyActivity(String email, LocalDate periodStart, LocalDate periodEnd) {
        return Optional.ofNullable(periodStart)
                .flatMap(start -> Optional.ofNullable(periodEnd)
                        .map(end -> commitDetailsService.getHourlyActivity(email, start, end)))
                .orElseGet(() -> commitDetailsService.getHourlyActivity(email));
    }

    private List<TaskWithCommitsDto> fetchTasks(String email, LocalDate periodStart, LocalDate periodEnd) {
        return Optional.ofNullable(periodStart)
                .flatMap(start -> Optional.ofNullable(periodEnd)
                        .map(end -> commitDetailsService.getTasksWithCommits(email, start, end)))
                .orElseGet(() -> commitDetailsService.getTasksWithCommits(email));
    }

    private Set<String> fetchRepositories(String email, LocalDate periodStart, LocalDate periodEnd) {
        return dailyStatsRepository.findRepositoriesByEmailAndPeriod(email, periodStart, periodEnd).stream()
                .filter(repo -> !"ALL_REPOS".equals(repo))
                .collect(Collectors.toSet());
    }

    private List<CommitDetailDto> fetchCommits(String email, LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart != null && periodEnd != null) {
            return commitDetailsService.getUserCommits(email, periodStart, periodEnd);
        }
        return commitDetailsService.getUserCommits(email);
    }

    private List<KaitenCardWithCommitsDto> fetchKaitenCards(
            UnifiedUser user,
            List<TaskWithCommitsDto> taskWithCommitsDtos,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {

        if (user == null || user.getKaitenId() == null) {
            log.debug("User has no kaiten_id");
            return Collections.emptyList();
        }
        List<CommitDetailDto> commits = taskWithCommitsDtos.stream()
                .map(TaskWithCommitsDto::getCommits)
                .flatMap(List<CommitDetailDto>::stream)
                .toList();
        Long kaitenUserId = user.getKaitenId();

        // 1. Получаем ID карточек пользователя за период
        List<Long> cardIds = kaitenCardMemberService.getCardIdsByUserId(kaitenUserId);
        if (cardIds.isEmpty()) {
            log.debug("User {} has no Kaiten cards in period", user.getEmail());
            return Collections.emptyList();
        }

        // 2. Получаем карточки по ID
        List<KaitenCard> cards = kaitenCardService.findByIds(cardIds);

        // 3. Группируем коммиты по ID карточки (извлекаем из task_number)
        Map<Long, List<CommitDetailDto>> commitsByCardId = commits.stream()
                .filter(c -> c.getTaskNumber() != null && !c.getTaskNumber().isBlank())
                .collect(Collectors.groupingBy(
                        commit -> extractKaitenCardIdFromTaskNumber(commit.getTaskNumber()),
                        Collectors.toList()
                ));

        // 4. Фильтруем карточки: открытые ИЛИ есть коммиты
        cards = cards.stream()
                .filter(card -> {
                    LocalDate createdDate = card.getCreatedAt().toLocalDate();

                    // 1. Проверка периода
                    boolean isInPeriod = (periodStart == null ||
                            (periodEnd == null || !createdDate.isAfter(periodEnd)));

                    // 2. Проверка статуса
                    KaitenCardStatus status = KaitenCardStatus.fromColumnId(card.getColumnId());
                    boolean isOpen = !status.isClosed();

                    // 3. Проверка наличия коммитов
                    boolean hasCommits = commitsByCardId.containsKey(card.getId());

                    return isInPeriod && (isOpen || hasCommits);
                })
                .collect(Collectors.toList());


        // 5. Собираем DTO
        return cards.stream()
                .map(card -> {
                    List<CommitDetailDto> cardCommits = commitsByCardId.getOrDefault(card.getId(), Collections.emptyList());

                    return KaitenCardWithCommitsDto.builder()
                            .id(card.getId())
                            .title(card.getTitle())
                            .status(KaitenCardStatus.fromColumnId(card.getColumnId()).getDisplayName())
                            .priority(card.getPriority())
                            .createdAt(card.getCreatedAt())
                            .closedAt(card.getClosedAt())
                            .lastMovedAt(card.getLastMovedAt())
                            .url(card.getUrl())
                            .commits(cardCommits)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Извлекает ID карточки Kaiten из номера задачи
     * Пример: "1700-2423436" -> 2423436
     */
    private Long extractKaitenCardIdFromTaskNumber(String taskNumber) {
        if (taskNumber == null || taskNumber.isBlank()) {
            return null;
        }

        // Ищем цифры после дефиса
        int lastDashIndex = taskNumber.lastIndexOf('-');
        if (lastDashIndex != -1 && lastDashIndex < taskNumber.length() - 1) {
            try {
                return Long.parseLong(taskNumber.substring(lastDashIndex + 1));
            } catch (NumberFormatException e) {
                // Не удалось распарсить
            }
        }

        // Если дефиса нет, пробуем распарсить всё число
        try {
            return Long.parseLong(taskNumber);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Long> calculateActivityByDay(List<DailyAuthorStats> userStats) {
        Map<String, Long> activityByDay = Arrays.stream(DayOfWeek.values())
                .collect(Collectors.toMap(
                        day -> day.getDisplayName(TextStyle.SHORT, RUSSIAN_LOCALE),
                        day -> 0L
                ));

        userStats.forEach(stat -> {
            String dayName = stat.getDate().getDayOfWeek().getDisplayName(TextStyle.SHORT, RUSSIAN_LOCALE);
            activityByDay.merge(dayName, stat.getCommits(), Long::sum);
        });

        return activityByDay;
    }

    private List<String> calculateInactivePeriods(List<DailyAuthorStats> userStats) {
        List<String> inactivePeriods = new ArrayList<>();
        LocalDate previousDate = null;

        for (DailyAuthorStats stat : userStats) {
            if (previousDate != null) {
                long daysBetween = ChronoUnit.DAYS.between(previousDate, stat.getDate());
                if (daysBetween > 1) {
                    LocalDate gapStart = previousDate.plusDays(1);
                    LocalDate gapEnd = stat.getDate().minusDays(1);
                    long gapDays = daysBetween - 1;
                    inactivePeriods.add(String.format("%s - %s (%d дня)",
                            gapStart.format(SHORT_DATE_FORMATTER),
                            gapEnd.format(SHORT_DATE_FORMATTER),
                            gapDays));
                }
            }
            previousDate = stat.getDate();
        }

        return inactivePeriods;
    }

    private record AggregatedStats(
            long totalCommits,
            long totalMergeCommits,
            long totalAdded,
            long totalDeleted,
            long totalTestAdded
    ) {}

    private AggregatedStats aggregateStats(List<DailyAuthorStats> userStats) {
        return userStats.stream()
                .reduce(
                        new AggregatedStats(0, 0, 0, 0, 0),
                        (acc, stat) -> new AggregatedStats(
                                acc.totalCommits() + stat.getCommits(),
                                acc.totalMergeCommits() + Optional.ofNullable(stat.getMergeCommits()).orElse(0L),
                                acc.totalAdded() + stat.getAddedLines(),
                                acc.totalDeleted() + stat.getDeletedLines(),
                                acc.totalTestAdded() + Optional.ofNullable(stat.getTestAddedLines()).orElse(0L)
                        ),
                        (acc1, acc2) -> new AggregatedStats(
                                acc1.totalCommits() + acc2.totalCommits(),
                                acc1.totalMergeCommits() + acc2.totalMergeCommits(),
                                acc1.totalAdded() + acc2.totalAdded(),
                                acc1.totalDeleted() + acc2.totalDeleted(),
                                acc1.totalTestAdded() + acc2.totalTestAdded()
                        )
                );
    }

    private String extractUsername(String email) {
        return Optional.ofNullable(email)
                .map(e -> e.split("@")[0])
                .orElse("");
    }

    private String generateRealSummary(String email, long totalCommits, long activeDays, long totalDays,
            LocalDate periodStart, LocalDate periodEnd,
            Map<String, Long> activityByDay,
            Map<Integer, Long> activityByHour,
            List<String> inactivePeriods,
            Set<String> repositories,
            List<TaskWithCommitsDto> tasks) {

        String reposStr = repositories.isEmpty() ? "неизвестных репозиториях" :
                String.join(", ", repositories);

        var peakDayEntry = activityByDay.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(Map.entry("неизвестно", 0L));
        String peakDay = peakDayEntry.getKey();
        long peakDayCommits = peakDayEntry.getValue();

        String peakHours = activityByHour.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(3)
                .map(e -> e.getKey() + ":00")
                .collect(Collectors.joining(", "));

        long commitsAfter8 = activityByHour.entrySet().stream()
                .filter(e -> e.getKey() >= 8)
                .mapToLong(Map.Entry::getValue)
                .sum();

        long weekendCommits = activityByDay.entrySet().stream()
                .filter(e -> e.getKey().equals("сб") || e.getKey().equals("вс"))
                .mapToLong(Map.Entry::getValue)
                .sum();

        String inactiveStr = inactivePeriods.isEmpty() ? "отсутствуют" :
                String.join(", ", inactivePeriods);

        String topTasksStr = tasks.stream()
                .limit(5)
                .map(t -> t.getTaskNumber() + " (" + String.valueOf(t.getCommits().size()) + " коммитов)")
                .collect(Collectors.joining(", "));

        return String.format("""
                ОБЩАЯ СВОДКА
                Разработчик %s работал в репозиториях: %s.
                За период %s - %s зафиксировано %d активных рабочих дней из %d.
                Всего коммитов: %d.
                Пик активности: %s (%d коммитов).
                Активные часы: %s.
                Коммиты после 8:00: %d.
                Коммиты в выходные: %d.
                Топ задач: %s.
                Простои: %s.
                """,
                extractUsername(email),
                reposStr,
                periodStart.format(DATE_FORMATTER),
                periodEnd.format(DATE_FORMATTER),
                activeDays,
                totalDays,
                totalCommits,
                peakDay,
                peakDayCommits,
                peakHours.isEmpty() ? "не определены" : peakHours,
                commitsAfter8,
                weekendCommits,
                topTasksStr.isEmpty() ? "не определены" : topTasksStr,
                inactiveStr
        );
    }
}