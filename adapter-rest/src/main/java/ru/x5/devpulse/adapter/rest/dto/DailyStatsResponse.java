package ru.x5.devpulse.adapter.rest.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import ru.x5.devpulse.domain.model.stats.DailyAuthorStats;

/** Дневной агрегат активности автора в репозитории. */
public record DailyStatsResponse(
        Long id,
        String email,
        LocalDate date,
        String repo,
        long commits,
        long mergeCommits,
        long addedLines,
        long deletedLines,
        long testAddedLines,
        LocalDateTime lastUpdated,
        Long userId
) {
    public static DailyStatsResponse from(DailyAuthorStats s) {
        return new DailyStatsResponse(
                s.id(),
                s.authorEmail().value(),
                s.date(),
                s.repo().value(),
                s.commits(), s.mergeCommits(),
                s.addedLines(), s.deletedLines(), s.testAddedLines(),
                s.lastUpdated(),
                s.userId());
    }
}
