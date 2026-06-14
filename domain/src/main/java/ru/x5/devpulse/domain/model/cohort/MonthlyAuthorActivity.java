package ru.x5.devpulse.domain.model.cohort;

import java.time.YearMonth;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Помесячный агрегат активности одного автора — вход для {@code CohortAssembler}.
 *
 * <p>Считается SQL'ем (GROUP BY email, month) над {@code daily_author_stats}: по строке на
 * {@code (email, месяц)}, только месяцы с активностью.</p>
 */
public record MonthlyAuthorActivity(
        Email email,
        YearMonth month,
        long commits,
        long mergeCommits,
        long addedLines,
        long deletedLines
) {

    /** Коммиты без мерджей — основа порога «активен» и scoring'а. */
    public long nonMergeCommits() {
        return Math.max(0, commits - mergeCommits);
    }
}
