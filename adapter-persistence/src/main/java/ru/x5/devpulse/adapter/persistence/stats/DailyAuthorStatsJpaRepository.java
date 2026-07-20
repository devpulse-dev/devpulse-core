package ru.x5.devpulse.adapter.persistence.stats;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DailyAuthorStatsJpaRepository extends JpaRepository<DailyAuthorStatsEntity, Long> {

    @Query("""
            select s from DailyAuthorStatsEntity s
             where s.date between :from and :to
            """)
    List<DailyAuthorStatsEntity> findByPeriod(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select s from DailyAuthorStatsEntity s
             where s.email = :email and s.date between :from and :to
            """)
    List<DailyAuthorStatsEntity> findByAuthorAndPeriod(
            @Param("email") String email,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Daily-агрегаты за период с опциональными фильтрами по автору/команде (фильтр в БД).
     *
     * <p>{@code email}/{@code team} == {@code null} → соответствующий фильтр отключён. {@code email}
     * ожидается уже в lower-case (инвариант {@code Email}); {@code lower(s.email)} — defence in depth.
     * Team — членство в {@code unified_user.team} через подзапрос (email нормализован к lower с обеих
     * сторон), как в hourly/monthly-агрегатах.</p>
     */
    @Query("""
            select s from DailyAuthorStatsEntity s
             where s.date between :from and :to
               and (:email is null or lower(s.email) = :email)
               and (:team is null
                    or lower(s.email) in (select lower(u.email) from UnifiedUserEntity u where u.team = :team))
             order by s.date, s.email
            """)
    List<DailyAuthorStatsEntity> findByPeriodFiltered(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("email") String email,
            @Param("team") String team);
}
