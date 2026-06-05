package ru.x5.devpulse.domain.model.review;

import ru.x5.devpulse.domain.model.user.Email;

/**
 * Ревью-метрики одного человека за период. Обслуживает {@code GET /api/v2/stats/reviews}.
 *
 * <p>Две стороны:</p>
 * <ul>
 *   <li><b>given</b> (что человек сделал для чужих MR): {@link #reviewsGiven} (нажал Approve,
 *       distinct по MR) и {@link #commentsGiven} (объём ревью-комментов);</li>
 *   <li><b>received</b> (что произошло с его MR): {@link #reviewsReceived},
 *       {@link #avgTimeToMergeHours}, {@link #mergedMrCount}.</li>
 * </ul>
 *
 * <p>{@link #displayName}/{@link #avatarUrl} дозаполняются enrichment'ом из {@code unified_user}
 * (на стадии агрегации — {@code null}, как у {@code AuthorSummary}).</p>
 */
public record ReviewAuthorStats(
        Email email,
        String displayName,
        String avatarUrl,
        int reviewsGiven,
        int commentsGiven,
        int reviewsReceived,
        double avgTimeToMergeHours,
        int mergedMrCount,
        String team,
        boolean lead
) {

    /** Копия с дозаполненными профильными полями (displayName/avatarUrl/команда/лид). */
    public ReviewAuthorStats withProfile(String displayName, String avatarUrl, String team, boolean lead) {
        return new ReviewAuthorStats(email, displayName, avatarUrl,
                reviewsGiven, commentsGiven, reviewsReceived, avgTimeToMergeHours, mergedMrCount, team, lead);
    }
}
