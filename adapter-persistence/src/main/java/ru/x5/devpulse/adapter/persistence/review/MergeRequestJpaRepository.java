package ru.x5.devpulse.adapter.persistence.review;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MergeRequestJpaRepository extends JpaRepository<MergeRequestEntity, Long> {

    /** MR, открытые в периоде (по created_at). */
    @Query("""
            select m
              from MergeRequestEntity m
             where m.createdAt between :from and :to
            """)
    List<MergeRequestEntity> findByCreatedAtBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
