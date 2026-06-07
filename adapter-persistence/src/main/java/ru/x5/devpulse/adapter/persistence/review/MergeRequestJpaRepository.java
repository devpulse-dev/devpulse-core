package ru.x5.devpulse.adapter.persistence.review;

import java.time.LocalDateTime;
import java.util.Collection;
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

    /**
     * Batch-lookup существующих MR по натуральному ключу GitLab — для upsert при сборе.
     *
     * <p>Заменяет N+1 (по одному {@code findBy} на MR). Запрос по двум IN-множествам даёт
     * <b>суперсет</b> (декартово {@code projectIds × iids} среди существующих строк); вызывающий
     * код фильтрует точные пары {@code (project_id, iid)} через map по композитному ключу
     * ({@code uk_merge_request_project_iid} гарантирует 1:1). Суперсет ограничен размером чанка,
     * т.к. iid'ы MR последовательны в пределах проекта.</p>
     */
    List<MergeRequestEntity> findByGitlabProjectIdInAndGitlabMrIidIn(
            Collection<Long> projectIds, Collection<Long> iids);
}
