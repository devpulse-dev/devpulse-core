package ru.x5.markable.dev.analytics.adapter.rest.dto;

import ru.x5.markable.dev.analytics.domain.model.stats.AuthorSummary;

/** Краткая статистика автора в ответах REST. */
public record AuthorSummaryResponse(
        String email,
        long commits,
        long mergeCommits,
        long addedLines,
        long deletedLines,
        long testAddedLines
) {
    public static AuthorSummaryResponse from(AuthorSummary s) {
        return new AuthorSummaryResponse(
                s.email().value(),
                s.commits(), s.mergeCommits(),
                s.addedLines(), s.deletedLines(), s.testAddedLines());
    }
}
