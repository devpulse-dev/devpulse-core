package ru.x5.markable.dev.analytics.domain.model.stats;

import java.util.Optional;
import ru.x5.markable.dev.analytics.domain.model.user.Email;

/**
 * Краткая статистика автора за период.
 *
 * <p>{@code displayName} и {@code avatarUrl} опциональны — заполняются из {@code unified_user}
 * на этапе enrichment в use case'е.</p>
 *
 * <p>{@link #activity()} — рассчитанный {@link ActivityScore}. Заполняется ТОЛЬКО в use case'ах,
 * где это уместно (например {@code GetDashboardService}). В query-эндпоинтах weekly/summary —
 * остаётся {@code null}.</p>
 */
public record AuthorSummary(
        Email email,
        String displayName,
        String avatarUrl,
        long commits,
        long mergeCommits,
        long addedLines,
        long deletedLines,
        long testAddedLines,
        ActivityScore activity
) {

    /** Конструктор без activity — для use case'ов, где score не считаем. */
    public AuthorSummary(Email email, String displayName, String avatarUrl,
                         long commits, long mergeCommits,
                         long addedLines, long deletedLines, long testAddedLines) {
        this(email, displayName, avatarUrl,
                commits, mergeCommits, addedLines, deletedLines, testAddedLines,
                /* activity = */ null);
    }

    /** Коммиты без мерджей. Используется для сортировки «по активности». */
    public long nonMergeCommits() {
        return Math.max(0, commits - mergeCommits);
    }

    public Optional<String> name() {
        return Optional.ofNullable(displayName);
    }

    public Optional<String> avatar() {
        return Optional.ofNullable(avatarUrl);
    }

    public Optional<ActivityScore> activityOptional() {
        return Optional.ofNullable(activity);
    }

    /** Возвращает копию с дополненными displayName и avatarUrl (для enrichment в use case). */
    public AuthorSummary withProfile(String displayName, String avatarUrl) {
        return new AuthorSummary(email, displayName, avatarUrl,
                commits, mergeCommits, addedLines, deletedLines, testAddedLines, activity);
    }

    /** Возвращает копию с проставленным {@link ActivityScore}. */
    public AuthorSummary withActivity(ActivityScore activity) {
        return new AuthorSummary(email, displayName, avatarUrl,
                commits, mergeCommits, addedLines, deletedLines, testAddedLines, activity);
    }
}
