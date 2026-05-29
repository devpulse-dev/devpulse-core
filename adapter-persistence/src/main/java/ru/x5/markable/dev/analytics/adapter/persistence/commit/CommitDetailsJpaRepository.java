package ru.x5.markable.dev.analytics.adapter.persistence.commit;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CommitDetailsJpaRepository extends JpaRepository<CommitDetailsEntity, Long> {

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

    /** Все хеши коммитов репозитория за период — для rebase-cleanup. */
    @Query("""
            select c.commitHash
              from CommitDetailsEntity c
             where c.repositoryName = :repo
               and c.commitDate between :from and :to
            """)
    List<String> findHashesByRepoAndPeriod(
            @Param("repo") String repo,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Bulk delete по списку хешей. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from CommitDetailsEntity c where c.commitHash in :hashes")
    int deleteByCommitHashes(@Param("hashes") Collection<String> hashes);
}
