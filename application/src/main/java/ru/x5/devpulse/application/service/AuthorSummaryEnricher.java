package ru.x5.devpulse.application.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.model.stats.AuthorSummary;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

/**
 * Enrichment-помощник: подтягивает {@code displayName} и {@code avatarUrl} из
 * {@link UnifiedUserRepository} к списку {@link AuthorSummary}.
 *
 * <p>Один batch {@code findByEmails} на всю коллекцию — даже если она «склеена»
 * из нескольких недель / страниц / топа. Авторы без записи в {@code unified_user}
 * возвращаются как есть (с {@code null}-полями).</p>
 */
final class AuthorSummaryEnricher {

    private final UnifiedUserRepository unifiedUserRepository;

    AuthorSummaryEnricher(UnifiedUserRepository unifiedUserRepository) {
        this.unifiedUserRepository = unifiedUserRepository;
    }

    /** Обогащает плоский список (использует Dashboard). */
    List<AuthorSummary> enrich(List<AuthorSummary> authors) {
        if (authors == null || authors.isEmpty()) return List.of();
        Map<Email, UnifiedUser> profiles = loadProfiles(collectEmails(authors));
        return apply(authors, profiles);
    }

    /**
     * Обогащает несколько разрозненных списков (Weekly: каждый week содержит свой authors-список).
     * Делает один общий batch-fetch профилей по всем уникальным email'ам, потом раздаёт.
     *
     * @return функция, которая для каждого входного списка вернёт enriched-копию
     */
    java.util.function.Function<List<AuthorSummary>, List<AuthorSummary>> batchEnricher(
            Collection<List<AuthorSummary>> groups) {
        Set<Email> all = new HashSet<>();
        for (List<AuthorSummary> g : groups) all.addAll(collectEmails(g));
        Map<Email, UnifiedUser> profiles = loadProfiles(all);
        return list -> apply(list, profiles);
    }

    private Map<Email, UnifiedUser> loadProfiles(Collection<Email> emails) {
        if (emails.isEmpty()) return Map.of();
        Map<Email, UnifiedUser> map = new HashMap<>(emails.size());
        for (UnifiedUser u : unifiedUserRepository.findByEmails(emails)) {
            map.put(u.email(), u);
        }
        return map;
    }

    private static Set<Email> collectEmails(List<AuthorSummary> authors) {
        Set<Email> emails = new HashSet<>(authors.size());
        for (AuthorSummary a : authors) emails.add(a.email());
        return emails;
    }

    private static List<AuthorSummary> apply(List<AuthorSummary> authors, Map<Email, UnifiedUser> profiles) {
        List<AuthorSummary> result = new ArrayList<>(authors.size());
        for (AuthorSummary a : authors) {
            UnifiedUser u = profiles.get(a.email());
            result.add(u == null ? a : a.withProfile(u.name(), u.avatarUrl(), u.team(), u.lead()));
        }
        return result;
    }
}
