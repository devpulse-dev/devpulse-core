package ru.x5.devpulse.application.port.out;

import java.util.Collection;
import java.util.List;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.stats.DailyAuthorStats;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Port out: персистентность daily-агрегатов.
 *
 * <p>В v2 источник правды для агрегатов — {@code commit_details}. Запись только через
 * {@link #recomputeFromCommits(Collection, Period)} — атомарный пересчёт per-repo (см.
 * {@code CollectGitStatsService}). Прямой {@code upsertAll} был в v1 (инкрементальные UPSERT'ы);
 * после фикса #4-5 он не нужен и удалён в N2-cleanup.</p>
 */
public interface DailyStatsRepository {

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
}
