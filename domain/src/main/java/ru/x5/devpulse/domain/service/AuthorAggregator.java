package ru.x5.devpulse.domain.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.git.RepoName;
import ru.x5.devpulse.domain.model.stats.DailyAuthorStats;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Группирует и суммирует коммиты в {@link DailyAuthorStats} по ключу {@code (email, date, repo)}.
 *
 * <p>Чистая функция: одни и те же входы → один и тот же результат. Никаких I/O, никакой БД.</p>
 */
public final class AuthorAggregator {

    private AuthorAggregator() {}

    public static List<DailyAuthorStats> aggregateByDay(Collection<Commit> commits) {
        if (commits == null || commits.isEmpty()) {
            return List.of();
        }
        Map<Key, Acc> accumulators = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        for (Commit c : commits) {
            LocalDate day = c.commitDate().toLocalDate();
            Key key = new Key(c.authorEmail(), day, c.repo());
            Acc acc = accumulators.computeIfAbsent(key, k -> new Acc());
            acc.commits++;
            if (c.merge()) {
                acc.mergeCommits++;
            }
            acc.added += c.addedLines();
            acc.deleted += c.deletedLines();
            acc.testAdded += c.testAddedLines();
        }

        List<DailyAuthorStats> result = new ArrayList<>(accumulators.size());
        for (Map.Entry<Key, Acc> e : accumulators.entrySet()) {
            Key k = e.getKey();
            Acc a = e.getValue();
            result.add(new DailyAuthorStats(
                    null,
                    k.email,
                    k.date,
                    k.repo,
                    a.commits,
                    a.mergeCommits,
                    a.added,
                    a.deleted,
                    a.testAdded,
                    now,
                    null
            ));
        }
        return result;
    }

    private record Key(Email email, LocalDate date, RepoName repo) {}

    private static final class Acc {
        long commits;
        long mergeCommits;
        long added;
        long deleted;
        long testAdded;
    }
}
