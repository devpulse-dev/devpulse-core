package ru.x5.markable.dev.analytics.gitlab.service.impl.helper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.DailyAuthorStats;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.AuthorWeeklySummaryDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.WeeklyCommitStatsDto;

/**
 * Вспомогательный класс для построения DTO недельной статистики.
 * Отвечает за создание объектов WeeklyCommitStatsDto из сгруппированных данных.
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class WeeklyStatsBuilder {

    private final StatsAggregator statsAggregator;

    /**
     * Строит DTO недельной статистики из сгруппированных данных.
     *
     * @param weeklyData карта, сгруппированная по неделям и авторам
     * @return список DTO недельной статистики, отсортированный по дате начала недели
     */
    public List<WeeklyCommitStatsDto> buildWeeklyStats(Map<Integer, Map<String, List<DailyAuthorStats>>> weeklyData) {
        List<WeeklyCommitStatsDto> result = new ArrayList<>();

        for (Map.Entry<Integer, Map<String, List<DailyAuthorStats>>> entry : weeklyData.entrySet()) {
            int weekKey = entry.getKey();
            int year = weekKey / 100;
            int week = weekKey % 100;
            Map<String, List<DailyAuthorStats>> userData = entry.getValue();

            LocalDate weekStart = statsAggregator.calculateWeekStart(year, week);
            LocalDate weekEnd = weekStart.plusDays(6);

            StatsAggregator.WeeklyAggregation aggregation = statsAggregator.aggregateWeeklyStats(userData);
            Map<String, AuthorWeeklySummaryDto> topAuthors = statsAggregator.sortAuthorsByCommits(aggregation.authors());

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
}
