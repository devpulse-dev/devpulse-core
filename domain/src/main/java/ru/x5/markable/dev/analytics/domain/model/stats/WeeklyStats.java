package ru.x5.markable.dev.analytics.domain.model.stats;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Статистика за одну ISO-неделю.
 *
 * @param weekStart понедельник недели
 */
public record WeeklyStats(
        int year,
        int week,
        LocalDate weekStart,
        long totalCommits,
        long totalMergeCommits,
        long totalAddedLines,
        long totalDeletedLines,
        long totalTestAddedLines,
        List<AuthorSummary> authors
) {

    public WeeklyStats {
        Objects.requireNonNull(weekStart, "weekStart required");
        authors = authors == null ? List.of() : List.copyOf(authors);
    }
}
