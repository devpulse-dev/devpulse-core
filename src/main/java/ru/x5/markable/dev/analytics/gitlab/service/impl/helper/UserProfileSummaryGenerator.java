package ru.x5.markable.dev.analytics.gitlab.service.impl.helper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.DailyAuthorStats;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.TaskWithCommitsDto;

/**
 * Вспомогательный класс для генерации сводки профиля пользователя.
 * Отвечает за расчёт метрик активности и создание текстового описания.
 */
@Component
@Log4j2
public class UserProfileSummaryGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM");
    private static final Locale RUSSIAN_LOCALE = Locale.forLanguageTag("ru");

    /**
     * Рассчитывает активность по дням недели.
     *
     * @param userStats список статистических записей
     * @return карта активности по дням недели
     */
    public Map<String, Long> calculateActivityByDay(List<DailyAuthorStats> userStats) {
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

    /**
     * Рассчитывает периоды неактивности пользователя.
     *
     * @param userStats список статистических записей
     * @return список периодов неактивности в текстовом формате
     */
    public List<String> calculateInactivePeriods(List<DailyAuthorStats> userStats) {
        List<String> inactivePeriods = new java.util.ArrayList<>();
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

    /**
     * Агрегирует статистику пользователя за период.
     *
     * @param userStats список статистических записей
     * @return агрегированная статистика
     */
    public AggregatedStats aggregateStats(List<DailyAuthorStats> userStats) {
        return userStats.stream()
                .reduce(
                        new AggregatedStats(0, 0, 0, 0, 0),
                        (acc, stat) -> new AggregatedStats(
                                acc.totalCommits() + stat.getCommits(),
                                acc.totalMergeCommits() + java.util.Optional.ofNullable(stat.getMergeCommits()).orElse(0L),
                                acc.totalAdded() + stat.getAddedLines(),
                                acc.totalDeleted() + stat.getDeletedLines(),
                                acc.totalTestAdded() + java.util.Optional.ofNullable(stat.getTestAddedLines()).orElse(0L)
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

    /**
     * Генерирует текстовую сводку о деятельности пользователя.
     *
     * @param email email пользователя
     * @param totalCommits общее количество коммитов
     * @param activeDays количество активных дней
     * @param totalDays общее количество дней
     * @param periodStart начало периода
     * @param periodEnd конец периода
     * @param activityByDay активность по дням недели
     * @param activityByHour активность по часам
     * @param inactivePeriods периоды неактивности
     * @param repositories множество репозиториев
     * @param tasks список задач
     * @return текстовая сводка
     */
    public String generateSummary(String email, long totalCommits, long activeDays, long totalDays,
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

    /**
     * Извлекает имя пользователя из email.
     *
     * @param email email пользователя
     * @return имя пользователя (часть до @)
     */
    public String extractUsername(String email) {
        return java.util.Optional.ofNullable(email)
                .map(e -> e.split("@")[0])
                .orElse("");
    }

    /**
     * Вспомогательный record для хранения агрегированной статистики.
     */
    public record AggregatedStats(
            long totalCommits,
            long totalMergeCommits,
            long totalAdded,
            long totalDeleted,
            long totalTestAdded
    ) {}
}
