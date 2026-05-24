package ru.x5.markable.dev.analytics.adapter.rest.dto;

import ru.x5.markable.dev.analytics.domain.model.stats.AuthorSummary;

/**
 * Краткая статистика автора в ответах REST.
 *
 * <p>{@code displayName} и {@code avatarUrl} могут быть {@code null}, если автор не связан
 * с записью в {@code unified_user} (например, ещё ни разу не синхронизировался с Kaiten).</p>
 *
 * <p>{@code nonMergeCommits} — производная метрика «реальной работы»
 * ({@code commits − mergeCommits}). По ней идёт ранжирование в дашборде.</p>
 */
public record AuthorSummaryResponse(
        String email,
        String displayName,
        String avatarUrl,
        long commits,
        long nonMergeCommits,
        long mergeCommits,
        long addedLines,
        long deletedLines,
        long testAddedLines
) {
    public static AuthorSummaryResponse from(AuthorSummary s) {
        return new AuthorSummaryResponse(
                s.email().value(),
                s.displayName(),
                s.avatarUrl(),
                s.commits(),
                s.nonMergeCommits(),
                s.mergeCommits(),
                s.addedLines(), s.deletedLines(), s.testAddedLines());
    }
}
