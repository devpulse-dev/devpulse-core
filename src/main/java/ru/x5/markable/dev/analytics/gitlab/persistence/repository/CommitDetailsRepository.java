package ru.x5.markable.dev.analytics.gitlab.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.CommitDetails;

@Repository
public interface CommitDetailsRepository extends JpaRepository<CommitDetails, Long> {

    List<CommitDetails> findByEmailOrderByCommitDateAsc(String email);

    List<CommitDetails> findByEmailAndCommitDateBetween(String email, LocalDateTime start, LocalDateTime end);

    boolean existsByCommitHash(String commitHash);

    Optional<CommitDetails> findByCommitHash(String commitHash);

    @Query("SELECT c.hour, COUNT(c) FROM CommitDetails c WHERE c.email = :email GROUP BY c.hour ORDER BY c.hour")
    List<Object[]> findHourlyActivityByEmail(@Param("email") String email);

    @Query("SELECT c.commitHash FROM CommitDetails c WHERE c.commitHash IN :hashes")
    List<String> findExistingHashes(@Param("hashes") List<String> hashes);

    void deleteByCommitDateBefore(LocalDateTime date);
}
