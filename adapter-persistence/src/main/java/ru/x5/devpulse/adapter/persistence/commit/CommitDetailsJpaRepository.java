package ru.x5.devpulse.adapter.persistence.commit;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CommitDetailsJpaRepository extends JpaRepository<CommitDetailsEntity, Long> {

    /**
     * Хеши, уже присутствующие в {@code commit_details}. Дедуп сбора: existing → {@code markSeen}
     * (не пересохраняются).
     *
     * <p><b>Инвариант — глобальность по {@code commit_hash}, не по репозиторию:</b> {@code commit_hash}
     * — UNIQUE во всей таблице, и матч идёт без {@code repository_name}. Это опирается на допущение,
     * что репозитории в {@code git.repositories} <b>независимы</b> (нет зеркал/форков с общей
     * историей — один и тот же SHA не встречается в двух репо). Если такой репозиторий появится,
     * общий коммит будет приписан ПЕРВОМУ собранному репо (при сборе второго он увидится как
     * existing), и per-repo метрики исказятся — тогда потребуется {@code UNIQUE(commit_hash,
     * repository_name)} + per-repo дедуп (матч по паре ключей).</p>
     */
    @Query("select c.commitHash from CommitDetailsEntity c where c.commitHash in :hashes")
    List<String> findExistingHashes(@Param("hashes") Collection<String> hashes);

    @Query("""
            select c
              from CommitDetailsEntity c
             where c.email = :email
               and c.commitDate between :from and :to
             order by c.commitDate desc
            """)
    List<CommitDetailsEntity> findByAuthorAndPeriod(
            @Param("email") String email,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    /**
     * Помечает существующие коммиты как «увидены в текущем сборе» (bump {@code collected_at}).
     *
     * <p>Часть mark-and-sweep: коммиты, всё ещё присутствующие в git, получают свежий
     * {@code collected_at}, чтобы последующий {@link #deleteZombies} их не снёс. Bulk-update
     * по уникальному индексу {@code commit_hash} — без подъёма строк в память.</p>
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("""
            update CommitDetailsEntity c
               set c.collectedAt = :seenAt
             where c.commitHash in :hashes
            """)
    int markSeen(@Param("hashes") Collection<String> hashes, @Param("seenAt") LocalDateTime seenAt);

    /**
     * Sweep rebase/force-push зомби: удаляет коммиты репозитория за период, которые НЕ были
     * «увидены» в текущем сборе ({@code collected_at < :seenBefore} либо {@code NULL}).
     *
     * <p>Set-разность считается в БД — heap не зависит от размера репозитория (в отличие от
     * старого подхода с загрузкой всех хешей в {@code Set}). См. P0-2 в REFACTORING.md.</p>
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("""
            delete from CommitDetailsEntity c
             where c.repositoryName = :repo
               and c.commitDate between :from and :to
               and (c.collectedAt is null or c.collectedAt < :seenBefore)
            """)
    int deleteZombies(
            @Param("repo") String repo,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("seenBefore") LocalDateTime seenBefore);

    /**
     * Почасовая агрегация не-мердж коммитов: GROUP BY (день недели, час) из {@code commit_date}.
     *
     * <p>Нативный запрос — JPQL не умеет {@code EXTRACT(ISODOW …)}. {@code ISODOW} даёт
     * 1=Пн … 7=Вс, минус 1 → наш контракт 0=Пн … 6=Вс. Час — {@code EXTRACT(HOUR …)}
     * из {@code commit_date} (не полагаемся на колонку {@code hour}: она nullable у старых строк).</p>
     *
     * <p><b>GROUP BY по выражениям, не по алиасам:</b> у {@code commit_details} есть реальная
     * колонка {@code hour}, и {@code group by hour} срезолвился бы в неё, а не в SELECT-алиас —
     * тогда {@code extract(...)} из SELECT оказались бы не сгруппированы. Поэтому группируем по
     * тем же выражениям, что в SELECT.</p>
     *
     * <p>{@code :email} / {@code :team} — независимые опциональные фильтры (null → без
     * ограничения; {@code cast(:param as text)} нужен Postgres, иначе bind-параметр null имеет
     * неопределённый тип). {@code :team} фильтрует по членству в команде через подзапрос на
     * {@code unified_user} (join по email, оба нормализованы к lower-case): коммиты авторов без
     * записи в {@code unified_user} либо в другой команде в выборку не входят. Несуществующая
     * команда → пустой подзапрос → пустой результат. Возвращает строки
     * {@code [weekday:int, hour:int, commits:long, addedLines:long]}.</p>
     */
    @Query(value = """
            select extract(isodow from commit_date)::int - 1 as weekday,
                   extract(hour   from commit_date)::int     as hour,
                   count(*)                                   as commits,
                   coalesce(sum(added_lines), 0)             as added_lines
              from commit_details
             where commit_date between :from and :to
               and is_merge = false
               and (cast(:email as text) is null or email = :email)
               and (cast(:team as text) is null
                    or email in (select u.email from unified_user u where u.team = :team))
             group by extract(isodow from commit_date)::int - 1,
                      extract(hour from commit_date)::int
            """, nativeQuery = true)
    List<Object[]> aggregateHourly(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("email") String email,
            @Param("team") String team);
}
