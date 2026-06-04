package ru.x5.devpulse.application.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import ru.x5.devpulse.application.port.in.GetReviewStatsUseCase;
import ru.x5.devpulse.application.port.out.ReviewStatsRepository;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.review.ReviewAuthorStats;
import ru.x5.devpulse.domain.model.review.ReviewStats;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.UnifiedUser;
import ru.x5.devpulse.domain.service.ReviewSummarizer;

/**
 * Ревью-метрики за период: агрегация {@link ReviewSummarizer} + enrichment
 * displayName/avatarUrl из {@code unified_user} (один batch-fetch по всем email'ам).
 */
@RequiredArgsConstructor
public final class GetReviewStatsService implements GetReviewStatsUseCase {

    private final ReviewStatsRepository reviewStatsRepository;
    private final UnifiedUserRepository unifiedUserRepository;

    @Override
    public ReviewStats get(Period period) {
        List<ReviewAuthorStats> authors =
                ReviewSummarizer.summarize(reviewStatsRepository.findMergeRequestsByPeriod(period));
        if (authors.isEmpty()) {
            return new ReviewStats(period, List.of());
        }

        Map<Email, UnifiedUser> profiles = loadProfiles(authors);
        List<ReviewAuthorStats> enriched = new ArrayList<>(authors.size());
        for (ReviewAuthorStats a : authors) {
            UnifiedUser u = profiles.get(a.email());
            enriched.add(u == null ? a : a.withProfile(u.name(), u.avatarUrl()));
        }
        return new ReviewStats(period, enriched);
    }

    private Map<Email, UnifiedUser> loadProfiles(List<ReviewAuthorStats> authors) {
        Set<Email> emails = new HashSet<>(authors.size());
        for (ReviewAuthorStats a : authors) emails.add(a.email());

        Map<Email, UnifiedUser> map = new HashMap<>(emails.size());
        for (UnifiedUser u : unifiedUserRepository.findByEmails(emails)) {
            map.put(u.email(), u);
        }
        return map;
    }
}
