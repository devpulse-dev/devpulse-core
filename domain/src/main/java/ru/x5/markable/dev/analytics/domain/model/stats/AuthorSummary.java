package ru.x5.markable.dev.analytics.domain.model.stats;

import java.util.Optional;
import ru.x5.markable.dev.analytics.domain.model.user.Email;

/**
 * Краткая статистика автора за период.
 *
 * <p>{@code displayName} и {@code avatarUrl} опциональны — заполняются из {@code unified_user}
 * на этапе enrichment в use case'е. Если пользователя нет в {@code unified_user}
 * (только что прилетел из git и ещё не связан с Kaiten) — оба будут {@code null}.</p>
 *
 * <p>{@link #nonMergeCommits()} — производный показатель «реальной работы»,
 * используется как первичная метрика для ранжирования в дашборде.</p>
 */
public record AuthorSummary(
        Email email,
        String displayName,
        String avatarUrl,
        long commits,
        long mergeCommits,
        long addedLines,
        long deletedLines,
        long testAddedLines
) {

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

    /** Возвращает копию с дополненными displayName и avatarUrl (для enrichment в use case). */
    public AuthorSummary withProfile(String displayName, String avatarUrl) {
        return new AuthorSummary(email, displayName, avatarUrl,
                commits, mergeCommits, addedLines, deletedLines, testAddedLines);
    }
}
