package ru.x5.markable.dev.analytics.domain.model.stats;

import java.util.List;
import java.util.Objects;
import ru.x5.markable.dev.analytics.domain.common.Period;

/**
 * Сводка активности за период по всем авторам.
 *
 * <p>Считается из набора {@link DailyAuthorStats} и не хранится в БД —
 * формируется on-the-fly query use case'ом.</p>
 *
 * @param topAuthors отсортированный по убыванию коммитов список авторов
 */
public record PeriodSummary(
        Period period,
        long totalCommits,
        long totalMergeCommits,
        long totalAddedLines,
        long totalDeletedLines,
        long totalTestAddedLines,
        int uniqueAuthors,
        List<AuthorSummary> topAuthors
) {

    public PeriodSummary {
        Objects.requireNonNull(period, "period required");
        topAuthors = topAuthors == null ? List.of() : List.copyOf(topAuthors);
    }

    /** Возвращает копию с новым списком top-авторов (для enrichment displayName/avatarUrl). */
    public PeriodSummary withTopAuthors(List<AuthorSummary> newTop) {
        return new PeriodSummary(
                period,
                totalCommits, totalMergeCommits, totalAddedLines, totalDeletedLines, totalTestAddedLines,
                uniqueAuthors,
                newTop);
    }
}
