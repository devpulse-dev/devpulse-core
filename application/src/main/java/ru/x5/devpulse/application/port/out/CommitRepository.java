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
     * Помечает уже существующие коммиты как «увидены в текущем сборе» (обновляет
     * {@code collected_at}). Часть mark-and-sweep rebase-cleanup: коммиты, всё ещё
     * присутствующие в git, защищаются от удаления в {@link #deleteZombies}.
     *
     * @param hashes хеши existing-коммитов из текущего батча
     * @param seenAt метка сбора (одинаковая на весь прогон репозитория)
     */
    void markSeen(Collection<CommitHash> hashes, java.time.LocalDateTime seenAt);

    /**
     * Удаляет rebase/force-push «зомби» — коммиты репозитория за период, которые НЕ были
     * увидены в текущем сборе ({@code collected_at < seenBefore}).
     *
     * <p>Set-разность считается в БД (а не загрузкой всех хешей репозитория в heap) — память
     * O(1) от размера репозитория. См. P0-2 в REFACTORING.md.</p>
     *
     * @param seenBefore метка начала сбора репозитория; всё, что старше — зомби
     * @return сколько строк удалено
     */
    int deleteZombies(RepoName repo, Period period, java.time.LocalDateTime seenBefore);

    /**
     * Почасовая агрегация не-мердж коммитов за период по ключу (день недели × час).
     *
     * <p>Считается в БД (GROUP BY по дню недели и часу из {@code commit_date}), без
     * подъёма коммитов в память. {@code weekday}: 0=Пн … 6=Вс (ISO).</p>
     *
     * <p>{@code author} и {@code team} — независимые опциональные фильтры. {@code team}
     * ограничивает выборку участниками команды (членство по {@code unified_user.team}):
     * авторы без сопоставленного пользователя или из другой команды не учитываются.</p>
     *
     * @param author пусто — без фильтра по автору; задан — только этот автор
     * @param team   пусто — без фильтра по команде; задан — только участники команды
     * @return непустые ячейки (commits &gt; 0); порядок не гарантируется
     */
    List<HourlyBucket> aggregateHourly(Period period, Optional<Email> author, Optional<String> team);
}
