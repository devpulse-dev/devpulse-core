package ru.x5.devpulse.adapter.rest.dto;

import java.time.LocalDate;
import java.util.List;
import ru.x5.devpulse.domain.model.stats.PeriodSummary;

/** Сводка за период: totals + top-N авторов. */
public record PeriodSummaryResponse(
        LocalDate from,
        LocalDate to,
        long totalCommits,
        long totalMergeCommits,
        long totalAddedLines,
        long totalDeletedLines,
        long totalTestAddedLines,
        int uniqueAuthors,
        List<AuthorSummaryResponse> topAuthors
) {
    public static PeriodSummaryResponse from(PeriodSummary s) {
        return new PeriodSummaryResponse(
                s.period().from(), s.period().to(),
                s.totalCommits(), s.totalMergeCommits(),
                s.totalAddedLines(), s.totalDeletedLines(), s.totalTestAddedLines(),
                s.uniqueAuthors(),
                s.topAuthors().stream().map(AuthorSummaryResponse::from).toList());
    }
}
