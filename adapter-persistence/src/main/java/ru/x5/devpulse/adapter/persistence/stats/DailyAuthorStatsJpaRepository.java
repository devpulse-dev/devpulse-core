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

    @Query("""
            select s from DailyAuthorStatsEntity s
             where s.repositoryName = :repo and s.date between :from and :to
            """)
    List<DailyAuthorStatsEntity> findByRepoAndPeriod(
            @Param("repo") String repo,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
