package ru.x5.devpulse.adapter.rest.dto;

import ru.x5.devpulse.domain.model.stats.AuthorSummary;

/**
 * Краткая статистика автора в ответах REST.
 *
 * <p>{@code displayName}, {@code avatarUrl} — могут быть {@code null} (нет записи в unified_user).
 * {@code activity} — заполняется только эндпоинтом {@code /dashboard} (в weekly/summary будет {@code null}).
 * {@code nonMergeCommits} — производная метрика ({@code commits − mergeCommits}).</p>
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
        long testAddedLines,
        ActivityScoreResponse activity
) {
    public static AuthorSummaryResponse from(AuthorSummary s) {
        return new AuthorSummaryResponse(
                s.email().value(),
                s.displayName(),
                s.avatarUrl(),
                s.commits(),
                s.nonMergeCommits(),
                s.mergeCommits(),
                s.addedLines(), s.deletedLines(), s.testAddedLines(),
                s.activity() == null ? null : ActivityScoreResponse.from(s.activity()));
    }
}
