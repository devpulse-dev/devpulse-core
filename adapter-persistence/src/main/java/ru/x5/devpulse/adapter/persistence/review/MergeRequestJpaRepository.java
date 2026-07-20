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
     * Кол-во вмерженных MR по авторам за период: {@code merged_at ∈ [from,to]}, автор ∈ emails.
     * Агрегация GROUP BY на стороне БД — в память MR не поднимаем. Interface-проекция
     * (author + count) маппится в домен адаптером.
     */
    @Query("""
            select m.authorEmail as authorEmail, count(m) as mergedCount
              from MergeRequestEntity m
             where m.mergedAt between :from and :to
               and m.authorEmail in :emails
               and m.targetBranch in :branches
             group by m.authorEmail
            """)
    List<AuthorMergedCountView> countMergedByAuthor(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("emails") Collection<String> emails,
            @Param("branches") Collection<String> branches);

    /** Проекция результата {@link #countMergedByAuthor}. */
    interface AuthorMergedCountView {
        String getAuthorEmail();

        long getMergedCount();
    }

    /**
     * Кол-во вмерженных MR по репозиториям за период: те же фильтры, GROUP BY проекта.
     * {@code min(web_url)} — представитель для парсинга пути репо (у всех MR проекта один хост/namespace).
     */
    @Query("""
            select m.gitlabProjectId as projectId,
                   min(m.webUrl) as sampleWebUrl,
                   count(m) as mergedCount
              from MergeRequestEntity m
             where m.mergedAt between :from and :to
               and m.authorEmail in :emails
               and m.targetBranch in :branches
             group by m.gitlabProjectId
            """)
    List<RepoMergedCountView> countMergedByRepo(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("emails") Collection<String> emails,
            @Param("branches") Collection<String> branches);

    /** Проекция результата {@link #countMergedByRepo}. */
    interface RepoMergedCountView {
        Long getProjectId();

        String getSampleWebUrl();

        long getMergedCount();
    }

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
