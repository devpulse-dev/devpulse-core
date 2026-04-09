package ru.x5.markable.dev.analytics.kaiten.persistence.repository;

import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenCard;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface KaitenCardRepository extends JpaRepository<KaitenCard, Long> {

    List<KaitenCard> findByOwnerId(Long ownerId);

    List<KaitenCard> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<KaitenCard> findByUpdatedAtAfter(LocalDateTime after);

    @Query("SELECT COUNT(c) FROM KaitenCard c WHERE c.status = :status")
    long countByStatus(@Param("status") String status);

    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (c.closed_at - c.created_at)) / 3600) FROM KaitenCard c WHERE c.closed_at IS NOT NULL", nativeQuery = true)
    Double getAverageCompletionTimeHours();

    @Query("SELECT c FROM KaitenCard c where c.id in (:ids)")
    List<KaitenCard> findByIds(@Param("ids") Collection<Long> ids);
}
