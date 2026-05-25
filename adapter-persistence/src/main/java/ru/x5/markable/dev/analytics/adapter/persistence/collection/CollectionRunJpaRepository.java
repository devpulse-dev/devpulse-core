package ru.x5.markable.dev.analytics.adapter.persistence.collection;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface CollectionRunJpaRepository extends JpaRepository<CollectionRunEntity, UUID> {

    @Query("""
            select max(r.untilDate)
              from CollectionRunEntity r
             where r.status = ru.x5.markable.dev.analytics.domain.model.collection.CollectionStatus.SUCCESS
            """)
    Optional<LocalDateTime> findLastSuccessfulUntil();
}
