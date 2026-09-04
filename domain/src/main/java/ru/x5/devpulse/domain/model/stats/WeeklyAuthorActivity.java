package ru.x5.devpulse.domain.model.stats;

import ru.x5.devpulse.domain.model.user.Email;

/**
 * Понедельный агрегат активности одного автора — вход для {@code StatsSummarizer.weeklyFromAggregates}.
 *
 * <p>Считается SQL'ем (GROUP BY email, ISO-год, ISO-неделя) над {@code daily_author_stats}: по строке
 * на {@code (email, неделя)}, только недели с активностью. В отличие от старого пути через
 * {@code findByPeriod}, не поднимает все daily-строки периода в heap.</p>
 */
public record WeeklyAuthorActivity(
        Email email,
        int isoYear,
        int isoWeek,
        long commits,
        long mergeCommits,
        long addedLines,
        long deletedLines,
        long testAddedLines
) {}
