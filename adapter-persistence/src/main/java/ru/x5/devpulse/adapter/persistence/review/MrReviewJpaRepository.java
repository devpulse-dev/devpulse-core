package ru.x5.devpulse.adapter.persistence.review;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MrReviewJpaRepository extends JpaRepository<MrReviewEntity, Long> {

    /** Все ревью для набора MR — для группировки по merge_request_id в адаптере. */
    List<MrReviewEntity> findByMergeRequestIdIn(Collection<Long> mergeRequestIds);

    /** Bulk-удаление ревью набора MR — для replace при повторном сборе (один запрос на чанк). */
    @Modifying
    @Query("delete from MrReviewEntity r where r.mergeRequestId in :mrIds")
    void deleteByMergeRequestIdIn(@Param("mrIds") Collection<Long> mergeRequestIds);
}
