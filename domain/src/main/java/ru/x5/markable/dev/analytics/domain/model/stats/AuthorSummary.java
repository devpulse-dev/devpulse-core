package ru.x5.markable.dev.analytics.domain.model.stats;

import ru.x5.markable.dev.analytics.domain.model.user.Email;

/**
 * Краткая статистика автора за период.
 */
public record AuthorSummary(
        Email email,
        long commits,
        long mergeCommits,
        long addedLines,
        long deletedLines,
        long testAddedLines
) {
}
