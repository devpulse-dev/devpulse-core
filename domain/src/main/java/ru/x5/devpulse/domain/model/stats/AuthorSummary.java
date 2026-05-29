package ru.x5.devpulse.domain.model.stats;

import ru.x5.devpulse.domain.model.user.Email;

/**
 * Краткая статистика автора за период.
 *
 * <p><b>Lifecycle:</b> запись проходит до трёх стадий — у каждой свои nullable поля:</p>
 * <ol>
 *   <li><b>Aggregation</b> ({@link ru.x5.devpulse.domain.service.StatsSummarizer},
 *       use case {@code aggregate()}). Заполнены только статистика;
 *       {@code displayName}, {@code avatarUrl}, {@code activity} = {@code null}.</li>
 *   <li><b>Enrichment</b> ({@code AuthorSummaryEnricher}, см. {@link #withProfile}).
 *       Дозаполнены {@code displayName}/{@code avatarUrl} из {@code unified_user}.</li>
 *   <li><b>Scoring</b> ({@code GetDashboardService}, см. {@link #withActivity}).
 *       Дозаполнен {@link ActivityScore}.</li>
 * </ol>
 *
 * <p>В REST/weekly/summary {@code activity} остаётся {@code null} — считаем score только
 * на дашборде. Маппер REST явно проверяет nullable и не падает.</p>
 *
 * <p><b>Дизайн-решение:</b> мы НЕ делаем sealed-иерархию {@code Plain}/{@code Scored} — это
 * было бы корректнее с точки зрения "types-as-states", но избыточно для одного nullable поля.
 * Цена: callsite-ы создают summary с явными {@code null} в конце. Это намеренно: явный {@code null}
 * лучше скрытого secondary-constructor'а.</p>
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

    /** Коммиты без мерджей. Используется для сортировки «по активности». */
    public long nonMergeCommits() {
        return Math.max(0, commits - mergeCommits);
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
