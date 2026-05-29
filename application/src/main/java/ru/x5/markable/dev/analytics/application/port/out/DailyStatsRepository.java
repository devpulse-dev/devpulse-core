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

    /**
     * Атомарно пересобирает daily-агрегаты для указанных авторов в указанном периоде из
     * текущего содержимого {@code commit_details}. Используется после сбора и cleanup'а,
     * чтобы агрегат всегда был зеркалом фактов в commit_details (защита от рассинхрона
     * при инкрементальных сборах и rebase'ах с force-push).
     *
     * <p>Реализация: транзакционный {@code DELETE WHERE LOWER(email) IN (?) AND date BETWEEN ?}
     * + {@code INSERT ... SELECT FROM commit_details ... GROUP BY (email, date, repo)}.</p>
     */
    void recomputeFromCommits(Collection<Email> emails, Period period);

    List<DailyAuthorStats> findByPeriod(Period period);

    List<DailyAuthorStats> findByAuthorAndPeriod(Email email, Period period);

    List<DailyAuthorStats> findByRepoAndPeriod(RepoName repo, Period period);
}
