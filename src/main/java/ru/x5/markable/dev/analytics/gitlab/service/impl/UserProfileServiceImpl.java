package ru.x5.markable.dev.analytics.gitlab.service.impl;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.DailyAuthorStats;
import ru.x5.markable.dev.analytics.gitlab.persistence.repository.DailyAuthorStatsRepository;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.CommitDetailDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.TaskWithCommitsDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.UserProfileDto;
import ru.x5.markable.dev.analytics.gitlab.service.CommitDetailsService;
import ru.x5.markable.dev.analytics.gitlab.service.UserProfileService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.stream.Collectors;

@Service
@Log4j2
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final DailyAuthorStatsRepository dailyStatsRepository;
    private final CommitDetailsService commitDetailsService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @Override
    public UserProfileDto getUserProfile(String email) {
        // Получаем все данные
        return getUserProfile(email, null, null);
    }

    @Override
    public UserProfileDto getUserProfile(String email, LocalDate periodStart, LocalDate periodEnd) {
        log.info("Fetching profile for user: {} with period: {} - {}", email, periodStart, periodEnd);

        // Фильтруем статистику по периоду, если указан
        List<DailyAuthorStats> userStats;
        if (periodStart != null && periodEnd != null) {
            userStats = dailyStatsRepository.findByEmailAndDateBetween(email, periodStart, periodEnd);
        } else {
            userStats = dailyStatsRepository.findByEmailOrderByDateAsc(email);
        }

        if (userStats.isEmpty()) {
            log.warn("No data found for user: {}", email);
            return null;
        }

        // Общая статистика
        long totalCommits = 0;
        long totalMergeCommits = 0;
        long totalAdded = 0;
        long totalDeleted = 0;
        long totalTestAdded = 0;

        // Для активности по дням недели
        Map<String, Long> activityByDay = new HashMap<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            activityByDay.put(day.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("ru")), 0L);
        }

        // Репозитории
        List<String> repositories = dailyStatsRepository.findRepositoriesByEmail(email);
        Set<String> repoSet = repositories.stream()
                .filter(repo -> !"ALL_REPOS".equals(repo))
                .collect(Collectors.toSet());

        // Первая и последняя даты в отфильтрованных данных
        LocalDate firstDate = userStats.get(0).getDate();
        LocalDate lastDate = userStats.get(userStats.size() - 1).getDate();
        long activeDays = userStats.size();
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(firstDate, lastDate) + 1;

        List<String> inactivePeriods = new ArrayList<>();
        LocalDate previousDate = null;

        for (DailyAuthorStats stat : userStats) {
            totalCommits += stat.getCommits();
            totalMergeCommits += stat.getMergeCommits() != null ? stat.getMergeCommits() : 0;
            totalAdded += stat.getAddedLines();
            totalDeleted += stat.getDeletedLines();
            totalTestAdded += stat.getTestAddedLines() != null ? stat.getTestAddedLines() : 0;

            // Активность по дням недели
            DayOfWeek dayOfWeek = stat.getDate().getDayOfWeek();
            String dayName = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("ru"));
            activityByDay.merge(dayName, stat.getCommits(), Long::sum);

            // Вычисляем простои
            if (previousDate != null) {
                long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(previousDate, stat.getDate());
                if (daysBetween > 1) {
                    LocalDate gapStart = previousDate.plusDays(1);
                    LocalDate gapEnd = stat.getDate().minusDays(1);
                    long gapDays = daysBetween - 1;
                    inactivePeriods.add(String.format("%s - %s (%d дня)",
                            gapStart.format(DateTimeFormatter.ofPattern("dd.MM")),
                            gapEnd.format(DateTimeFormatter.ofPattern("dd.MM")),
                            gapDays));
                }
            }
            previousDate = stat.getDate();
        }

        // Часовая активность (тоже фильтруем по периоду)
        Map<Integer, Long> activityByHour;
        if (periodStart != null && periodEnd != null) {
            activityByHour = commitDetailsService.getHourlyActivity(email, periodStart, periodEnd);
        } else {
            activityByHour = commitDetailsService.getHourlyActivity(email);
        }

        // Задачи (фильтруем по периоду)
        List<TaskWithCommitsDto> tasks;
        if (periodStart != null && periodEnd != null) {
            tasks = commitDetailsService.getTasksWithCommits(email, periodStart, periodEnd);
        } else {
            tasks = commitDetailsService.getTasksWithCommits(email);
        }

        // Среднее количество коммитов в день
        double avgCommitsPerDay = (double) totalCommits / activeDays;

        String aiSummary = generateRealSummary(
                email, totalCommits, activeDays, totalDays,
                firstDate, lastDate,
                activityByDay, activityByHour,
                inactivePeriods,
                repoSet,
                tasks
        );

        return UserProfileDto.builder()
                .email(email)
                .username(extractUsername(email))
                .joinedDate(firstDate)
                .totalCommits(totalCommits)
                .totalMergeCommits(totalMergeCommits)
                .totalAddedLines(totalAdded)
                .totalDeletedLines(totalDeleted)
                .totalTestAddedLines(totalTestAdded)
                .activeDays(activeDays)
                .totalDays(totalDays)
                .avgCommitsPerDay(avgCommitsPerDay)
                .periodStart(firstDate)
                .periodEnd(lastDate)
                .activityByDay(activityByDay)
                .activityByHour(activityByHour)
                .repositories(new ArrayList<>(repoSet))
                .tasks(tasks)
                .inactivePeriods(inactivePeriods)
                .aiSummary(aiSummary)
                .build();
    }


    @Override
    public List<CommitDetailDto> getUserCommits(String email) {
        log.info("Fetching commits for user: {}", email);
        return commitDetailsService.getUserCommits(email);
    }

    private String extractUsername(String email) {
        if (email == null) {
            return "";
        }
        return email.split("@")[0];
    }

    /**
     * Реальный метод для генерации AI Summary с данными
     */
    private String generateRealSummary(String email, long totalCommits, long activeDays, long totalDays,
            LocalDate periodStart, LocalDate periodEnd,
            Map<String, Long> activityByDay,
            Map<Integer, Long> activityByHour,
            List<String> inactivePeriods,
            Set<String> repositories,
            List<TaskWithCommitsDto> tasks) {

        String reposStr = repositories.isEmpty() ? "неизвестных репозиториях" :
                String.join(", ", repositories);

        String peakDay = activityByDay.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("неизвестно");
        long peakDayCommits = activityByDay.getOrDefault(peakDay, 0L);

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
                .map(t -> t.getTaskNumber() + " (" + t.getCommits().size() + " коммитов)")
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
