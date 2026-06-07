package ru.x5.devpulse.adapter.persistence.collection;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CollectionRunJpaRepository extends JpaRepository<CollectionRunEntity, UUID> {

    @Query("""
            select max(r.untilDate)
              from CollectionRunEntity r
             where r.status = ru.x5.devpulse.domain.model.collection.CollectionStatus.SUCCESS
            """)
    Optional<LocalDateTime> findLastSuccessfulUntil();

    /** Самый свежий прогон по startedAt (идущий = самый свежий, т.к. сбор single-flight). */
    Optional<CollectionRunEntity> findFirstByOrderByStartedAtDesc();

    /** Переводит все RUNNING → FAILED (startup-реконсиляция осиротевших прогонов). */
    @Modifying
    @Query("""
            update CollectionRunEntity r
               set r.status = ru.x5.devpulse.domain.model.collection.CollectionStatus.FAILED,
                   r.finishedAt = :now,
                   r.errorMessage = :reason
             where r.status = ru.x5.devpulse.domain.model.collection.CollectionStatus.RUNNING
            """)
    int failOrphanedRunning(@Param("now") LocalDateTime now, @Param("reason") String reason);

    /** Ставит флаг отмены (cancel-эндпоинт). */
    @Modifying
    @Query("update CollectionRunEntity r set r.cancelRequested = true where r.id = :id")
    void markCancelRequested(@Param("id") UUID id);

    /** Читается бегущим сбором на checkpoint'ах. */
    @Query("select r.cancelRequested from CollectionRunEntity r where r.id = :id")
    Optional<Boolean> findCancelRequested(@Param("id") UUID id);
}
