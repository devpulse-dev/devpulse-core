package ru.x5.markable.dev.analytics.gitlab.service.impl.helper;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.x5.markable.dev.analytics.gitlab.model.AuthorAggregate;
import ru.x5.markable.dev.analytics.gitlab.model.CommitDetail;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.DailyAuthorStats;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.AuthorSummaryDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.AuthorWeeklySummaryDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.DailyCommitStatsDto;

/**
 * Вспомогательный класс для агрегации статистики коммитов.
 * Отвечает за группировку и суммирование статистических данных.
 */
@Component
@Log4j2
public class StatsAggregator {

    /**
     * Группирует коммиты по дням и пользователям.
     * Пропускает коммиты старше минимально допустимой даты (01.01.2026).
     *
     * @param commits список деталей коммитов
     * @return карта, сгруппированная по датам и email авторов
     */
    public Map<LocalDate, Map<String, AuthorAggregate>> groupCommitsByDay(List<CommitDetail> commits) {
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
                    AuthorAggregate::merge);
        }

        return dailyStats;
    }

    /**
     * Агрегирует статистику по дням, суммируя показатели всех авторов за каждый день.
     *
     * @param stats список статистических записей
     * @return карта, сгруппированная по датам с агрегированной статистикой
     */
    public Map<LocalDate, DailyCommitStatsDto> aggregateDailyStats(List<DailyAuthorStats> stats) {
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

    /**
     * Агрегирует статистику за период, суммируя показатели по всем авторам.
     *
     * @param stats список статистических записей
     * @return объект агрегации с суммарными показателями и картой авторов
     */
    public PeriodSummaryAggregation aggregatePeriodStats(List<DailyAuthorStats> stats) {
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
    public Map<String, AuthorSummaryDto> extractTopAuthors(Map<String, AuthorSummaryDto> authorMap) {
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
     * Группирует статистику по неделям и авторам.
     * Ключ карты - уникальный идентификатор недели (год * 100 + номер недели).
     *
     * @param stats список статистических записей
     * @return карта, сгруппированная по неделям и авторам
     */
    public Map<Integer, Map<String, List<DailyAuthorStats>>> groupStatsByWeek(List<DailyAuthorStats> stats) {
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
     * Агрегирует статистику за неделю для всех авторов.
     *
     * @param userData карта авторов с их статистическими записями за неделю
     * @return объект агрегации с суммарными показателями и картой авторов
     */
    public WeeklyAggregation aggregateWeeklyStats(Map<String, List<DailyAuthorStats>> userData) {
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
    public Map<String, AuthorWeeklySummaryDto> sortAuthorsByCommits(Map<String, AuthorWeeklySummaryDto> authors) {
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
     * Вычисляет дату начала недели по ISO стандарту.
     *
     * @param year год
     * @param week номер недели (1-53)
     * @return дата начала недели (понедельник)
     */
    public LocalDate calculateWeekStart(int year, int week) {
        return LocalDate.of(year, 1, 1)
                .with(WeekFields.ISO.weekOfWeekBasedYear(), week)
                .with(WeekFields.ISO.dayOfWeek(), 1);
    }

    /**
     * Находит минимальную дату в списке статистики.
     *
     * @param stats список статистических записей
     * @return минимальная дата или null если список пуст
     */
    public LocalDate findMinDate(List<DailyAuthorStats> stats) {
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
    public LocalDate findMaxDate(List<DailyAuthorStats> stats) {
        return stats.stream()
                .map(DailyAuthorStats::getDate)
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    /**
     * Вспомогательный record для хранения агрегированной статистики за период.
     */
    public record PeriodSummaryAggregation(
            long totalCommits,
            long totalMergeCommits,
            long totalAdded,
            long totalDeleted,
            long totalTestAdded,
            Map<String, AuthorSummaryDto> authorMap
    ) {}

    /**
     * Вспомогательный record для хранения агрегированной недельной статистики.
     */
    public record WeeklyAggregation(
            long totalCommits,
            long totalMergeCommits,
            long totalAdded,
            long totalDeleted,
            long totalTestAdded,
            Map<String, AuthorWeeklySummaryDto> authors
    ) {}
}
