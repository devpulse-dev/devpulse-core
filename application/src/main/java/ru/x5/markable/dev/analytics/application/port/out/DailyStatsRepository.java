package ru.x5.markable.dev.analytics.application.port.out;

import java.util.Collection;
import java.util.List;
import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.model.git.RepoName;
import ru.x5.markable.dev.analytics.domain.model.stats.DailyAuthorStats;
import ru.x5.markable.dev.analytics.domain.model.user.Email;

/**
 * Port out: персистентность daily-агрегатов.
 *
 * <p>Сохранение — bulk upsert по уникальному ключу {@code (email, date, repo)}.
 * Чтение — по периоду, опционально с фильтром по автору.</p>
 */
public interface DailyStatsRepository {

    /** Bulk upsert (один INSERT ... ON CONFLICT DO UPDATE). */
    void upsertAll(Collection<DailyAuthorStats> stats);

    List<DailyAuthorStats> findByPeriod(Period period);

    List<DailyAuthorStats> findByAuthorAndPeriod(Email email, Period period);

    List<DailyAuthorStats> findByRepoAndPeriod(RepoName repo, Period period);
}
