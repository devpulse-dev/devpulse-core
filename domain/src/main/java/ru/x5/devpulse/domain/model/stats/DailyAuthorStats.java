package ru.x5.devpulse.domain.model.stats;

import java.time.LocalDate;
import java.time.LocalDateTime;
import ru.x5.devpulse.domain.model.git.RepoName;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Агрегат активности автора в репозитории за один день.
 *
 * <p>Ключ уникальности: {@code (email, date, repo)}. Все счётчики &ge; 0.</p>
 */
public record DailyAuthorStats(
        Long id,
        Email authorEmail,
        LocalDate date,
        RepoName repo,
        long commits,
        long mergeCommits,
        long addedLines,
        long deletedLines,
        long testAddedLines,
        LocalDateTime lastUpdated,
        Long userId
) {

    public DailyAuthorStats {
        if (commits < 0 || mergeCommits < 0
                || addedLines < 0 || deletedLines < 0 || testAddedLines < 0) {
            throw new IllegalArgumentException("daily stats counters must be non-negative");
        }
    }

    /** Возвращает копию с проставленным {@code userId} (для связывания с unified_user). */
    public DailyAuthorStats withUserId(Long userId) {
        return new DailyAuthorStats(
                id, authorEmail, date, repo,
                commits, mergeCommits, addedLines, deletedLines, testAddedLines,
                lastUpdated, userId);
    }

    /** Сложение двух дневных агрегатов одного и того же ключа. */
    public DailyAuthorStats plus(DailyAuthorStats other) {
        if (!authorEmail.equals(other.authorEmail)
                || !date.equals(other.date)
                || !repo.equals(other.repo)) {
            throw new IllegalArgumentException("cannot merge stats with different keys");
        }
        return new DailyAuthorStats(
                id,
                authorEmail,
                date,
                repo,
                commits + other.commits,
                mergeCommits + other.mergeCommits,
                addedLines + other.addedLines,
                deletedLines + other.deletedLines,
                testAddedLines + other.testAddedLines,
                lastUpdated,
                userId
        );
    }
}
