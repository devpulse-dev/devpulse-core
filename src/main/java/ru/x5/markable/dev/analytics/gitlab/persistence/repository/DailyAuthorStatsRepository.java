package ru.x5.markable.dev.analytics.gitlab.persistence.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.DailyAuthorStats;

@Repository
public interface DailyAuthorStatsRepository extends JpaRepository<DailyAuthorStats, Long> {

    List<DailyAuthorStats> findByDateBetween(LocalDate start, LocalDate end);

    List<DailyAuthorStats> findByDate(LocalDate date);

    List<DailyAuthorStats> findByEmailInAndDate(List<String> emails, LocalDate date);

    @Modifying
    @Query("DELETE FROM DailyAuthorStats d WHERE d.date < :date")
    int deleteOlderThan(@Param("date") LocalDate date);

    List<DailyAuthorStats> findByEmail(String email);

    List<DailyAuthorStats> findByEmailOrderByDateAsc(String email);

    Optional<DailyAuthorStats> findByEmailAndDateAndRepositoryName(String email, LocalDate date, String repositoryName);

    @Query("SELECT DISTINCT d.repositoryName FROM DailyAuthorStats d WHERE d.email = :email")
    List<String> findRepositoriesByEmail(@Param("email") String email);

    @Query("SELECT d FROM DailyAuthorStats d WHERE d.email = :email AND d.date BETWEEN :start AND :end ORDER BY d.date ASC")
    List<DailyAuthorStats> findByEmailAndDateBetween(
            @Param("email") String email,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

}
