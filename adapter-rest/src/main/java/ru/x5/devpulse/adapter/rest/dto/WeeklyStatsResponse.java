package ru.x5.devpulse.adapter.rest.dto;

import java.time.LocalDate;
import java.util.List;
import ru.x5.devpulse.domain.model.stats.WeeklyStats;

/** Недельная статистика: totals + per-author breakdown. */
public record WeeklyStatsResponse(
        int year,
        int week,
        LocalDate weekStart,
        long totalCommits,
        long totalMergeCommits,
        long totalAddedLines,
        long totalDeletedLines,
        long totalTestAddedLines,
        List<AuthorSummaryResponse> authors
) {
    public static WeeklyStatsResponse from(WeeklyStats w) {
        return new WeeklyStatsResponse(
                w.year(), w.week(), w.weekStart(),
                w.totalCommits(), w.totalMergeCommits(),
                w.totalAddedLines(), w.totalDeletedLines(), w.totalTestAddedLines(),
                w.authors().stream().map(AuthorSummaryResponse::from).toList());
    }
}
