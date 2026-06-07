package ru.x5.devpulse.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import ru.x5.devpulse.domain.common.PageRequest;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.git.CommitHash;
import ru.x5.devpulse.domain.model.git.RepoName;
import ru.x5.devpulse.domain.model.stats.HourlyBucket;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Port out: персистентность коммитов.
 */
public interface CommitRepository {

    /** Какие хеши уже есть в БД — для пропуска дубликатов при batch-вставке. */
    Set<CommitHash> findExistingHashes(Collection<CommitHash> hashes);

    /**
     * Batch-вставка новых коммитов.
     *
     * <p>Маппинг {@code email → user_id} разрешается <b>в use case</b> (через
     * {@link UnifiedUserRepository#findOrCreateAll}) и передаётся готовым: адаптер
     * только пишет, не управляет identity. Для коммита без email или автора, которого
     * нет в карте, {@code user_id} остаётся {@code null} (FK nullable).</p>
     *
     * @param commits     новые коммиты (уже отдедуплены по хешу в use case)
     * @param userByEmail предразрешённые user_id по email автора
     */
    void saveAll(Collection<Commit> commits, Map<Email, Long> userByEmail);

    /** Список коммитов пользователя за период с пагинацией. */
    List<Commit> findByAuthor(Email email, Period period, PageRequest page);

    /**
     * Все хеши коммитов в указанном репозитории за период.
     *
     * <p>Используется для rebase-cleanup: сравниваем со списком хешей, фактически пришедших
     * из {@code git log} в этом сборе. Хеши которые в БД есть, а в git'е нет — «зомби»
     * после force-push, их удаляем через {@link #deleteByHashes}.</p>
     */
    Set<CommitHash> findHashesByRepoAndPeriod(RepoName repo, Period period);

    /** Bulk-удаление коммитов по хешам. Используется для rebase-cleanup. */
    void deleteByHashes(Collection<CommitHash> hashes);

    /**
     * Почасовая агрегация не-мердж коммитов за период по ключу (день недели × час).
     *
     * <p>Считается в БД (GROUP BY по дню недели и часу из {@code commit_date}), без
     * подъёма коммитов в память. {@code weekday}: 0=Пн … 6=Вс (ISO).</p>
     *
     * @param author пусто — агрегат по всей команде; задан — только этот автор
     * @return непустые ячейки (commits &gt; 0); порядок не гарантируется
     */
    List<HourlyBucket> aggregateHourly(Period period, Optional<Email> author);
}
