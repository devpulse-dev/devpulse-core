package ru.x5.devpulse.adapter.persistence.review;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.x5.devpulse.application.port.out.ReviewWriteRepository;
import ru.x5.devpulse.domain.model.review.CollectedMergeRequest;
import ru.x5.devpulse.domain.model.review.MrReview;

/**
 * Запись собранных MR/ревью. Идемпотентно: upsert MR по {@code (project_id, iid)},
 * ревью пересобираются (delete + insert) — повторный сбор не плодит дублей.
 *
 * <p><b>Производительность.</b> Бэкфилл может приносить десятки тысяч MR за раз. Пишем чанками
 * по {@link #CHUNK}; после каждого — {@code em.clear()}, чтобы прочитанные batch-SELECT'ом
 * сущности не копились в persistence-context. Старые ревью чанка удаляются одним bulk-запросом.</p>
 *
 * <p><b>Батчинг MR.</b> Сами {@code merge_request} пишутся одним native
 * {@code INSERT … ON CONFLICT (project_id, iid) DO UPDATE} на чанк (идемпотентно по натуральному
 * ключу), а не построчным {@code mrJpa.save()} — тот на {@code IDENTITY} давал N round-trips
 * (десятки тысяч одиночных INSERT на бэкфилле; та же причина, что в ADR-11 для commit_details).
 * Id всех MR чанка (новых и обновлённых) добираются одним batch-SELECT по паре ключей.</p>
 *
 * <p>Строки {@code mr_review} тоже пишутся через native {@link JdbcTemplate#batchUpdate}
 * ({@code MrReviewEntity} на {@code IDENTITY} не батчился JPA-saveAll'ом), и пересобираются
 * целиком (bulk-delete по mr_id всех MR чанка + insert) — replace-семантика без дублей.</p>
 */
@Component
@Log4j2
@RequiredArgsConstructor
class ReviewWriteRepositoryAdapter implements ReviewWriteRepository {

    private static final int MAX_TEXT = 1000;
    private static final int CHUNK = 500;

    private static final String REVIEW_INSERT_SQL = """
            INSERT INTO mr_review (merge_request_id, reviewer_email, approved, comment_count, collected_at)
            VALUES (?, ?, ?, ?, ?)
            """;

    /**
     * Bulk upsert MR по натуральному ключу {@code (gitlab_project_id, gitlab_mr_iid)}
     * (unique-constraint {@code uk_merge_request_project_iid}, миграция 022). {@code EXCLUDED.*} —
     * значения из VALUES при конфликте; {@code id} (IDENTITY) сохраняется у существующей строки.
     */
    private static final String MR_UPSERT_SQL = """
            INSERT INTO merge_request
                (gitlab_project_id, gitlab_mr_iid, author_email, title, web_url, state,
                 target_branch, created_at, merged_at, collected_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (gitlab_project_id, gitlab_mr_iid) DO UPDATE SET
                author_email  = EXCLUDED.author_email,
                title         = EXCLUDED.title,
                web_url       = EXCLUDED.web_url,
                state         = EXCLUDED.state,
                target_branch = EXCLUDED.target_branch,
                created_at    = EXCLUDED.created_at,
                merged_at     = EXCLUDED.merged_at,
                collected_at  = EXCLUDED.collected_at
            """;

    private final MergeRequestJpaRepository mrJpa;
    private final MrReviewJpaRepository reviewJpa;
    private final JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public void upsert(List<CollectedMergeRequest> mergeRequests) {
        if (mergeRequests == null || mergeRequests.isEmpty()) return;
        // Дедуп по натуральному ключу (project_id, iid), last-wins. Offset-пагинация GitLab ШТАТНО
        // возвращает дубли на границах страниц у активного проекта (новые MR сдвигают окно). Дубль
        // в одном чанке ронял бы native ON CONFLICT ("cannot affect row a second time" при
        // reWriteBatchedInserts) либо uk_mr_review_mr_reviewer (ревью вставились бы дважды) → откат
        // транзакции и потеря всех ревью проекта. Дедуп до чанкинга снимает обе поломки.
        List<CollectedMergeRequest> deduped = dedupLastWins(mergeRequests);
        LocalDateTime now = LocalDateTime.now();
        int total = deduped.size();

        for (int start = 0; start < total; start += CHUNK) {
            int end = Math.min(start + CHUNK, total);
            upsertChunk(deduped.subList(start, end), now);
            em.clear(); // отцепляем прочитанные findBy-сущности — persistence context не растёт на бэкфилле
            log.info("Записано {}/{} MR с ревью", end, total);
        }
        log.info("Записано/обновлено {} MR с ревью ({} дублей пагинации отброшено)",
                total, mergeRequests.size() - total);
    }

    /** Дедуп по (project_id, iid), last-wins; порядок первого появления сохраняется. */
    private static List<CollectedMergeRequest> dedupLastWins(List<CollectedMergeRequest> mrs) {
        LinkedHashMap<MrKey, CollectedMergeRequest> byKey = new LinkedHashMap<>(mrs.size());
        for (CollectedMergeRequest c : mrs) {
            byKey.put(new MrKey(c.gitlabProjectId(), c.gitlabMrIid()), c);
        }
        return new ArrayList<>(byKey.values());
    }

    private void upsertChunk(List<CollectedMergeRequest> chunk, LocalDateTime now) {
        // 1. Bulk UPSERT MR по натуральному ключу (project_id, iid) — идемпотентно, одним batch,
        //    без построчного save() (IDENTITY давал N round-trips на десятки тысяч MR).
        //    В чанке дублей (project, iid) нет — MR проекта уникальны по iid — ON CONFLICT безопасен.
        jdbcTemplate.batchUpdate(MR_UPSERT_SQL, chunk, chunk.size(), (ps, c) -> {
            ps.setLong(1, c.gitlabProjectId());
            ps.setLong(2, c.gitlabMrIid());
            ps.setString(3, c.author().value());
            ps.setString(4, truncate(c.title()));
            ps.setString(5, truncate(c.webUrl()));
            ps.setString(6, c.state() == null || c.state().isBlank() ? "unknown" : c.state());
            ps.setString(7, c.targetBranch());
            ps.setObject(8, c.createdAt());
            ps.setObject(9, c.mergedAt());
            ps.setObject(10, now);
        });

        // 2. Один batch-SELECT id по (project_id, iid) для всех MR чанка (новых и обновлённых).
        //    Суперсет project IN(..) AND iid IN(..) разрешается по паре ключей через MrKey
        //    (одинаковый iid в разных проектах не путается).
        Set<Long> projectIds = new HashSet<>();
        Set<Long> iids = new HashSet<>();
        for (CollectedMergeRequest c : chunk) {
            projectIds.add(c.gitlabProjectId());
            iids.add(c.gitlabMrIid());
        }
        Map<MrKey, Long> idByKey = new HashMap<>();
        for (MergeRequestEntity e : mrJpa.findByGitlabProjectIdInAndGitlabMrIidIn(projectIds, iids)) {
            idByKey.put(new MrKey(e.getGitlabProjectId(), e.getGitlabMrIid()), e.getId());
        }

        // 3. Ревью пересобираем целиком: bulk-delete по mr_id всех MR чанка + native batch insert.
        //    Delete по всем id (для новых MR это no-op) — проще, чем различать new/existing, и корректно.
        List<Long> mrIds = new ArrayList<>(chunk.size());
        List<ReviewRow> reviewRows = new ArrayList<>();
        for (CollectedMergeRequest c : chunk) {
            Long mrId = idByKey.get(new MrKey(c.gitlabProjectId(), c.gitlabMrIid()));
            if (mrId == null) {
                // после успешного upsert строка обязана существовать; страхуемся от потери ревью
                log.warn("MR {}!{} не найден после upsert — ревью пропущены",
                        c.gitlabProjectId(), c.gitlabMrIid());
                continue;
            }
            mrIds.add(mrId);
            for (MrReview r : c.reviews()) {
                reviewRows.add(new ReviewRow(mrId, r.reviewer().value(), r.approved(), r.commentCount()));
            }
        }
        if (!mrIds.isEmpty()) {
            reviewJpa.deleteByMergeRequestIdIn(mrIds);
        }
        insertReviews(reviewRows, now);
    }

    /** Native batch insert строк ревью — обход IDENTITY (см. javadoc класса / ADR-11). */
    private void insertReviews(List<ReviewRow> rows, LocalDateTime now) {
        if (rows.isEmpty()) return;
        jdbcTemplate.batchUpdate(REVIEW_INSERT_SQL, rows, CHUNK, (ps, r) -> {
            ps.setLong(1, r.mrId());
            ps.setString(2, r.reviewerEmail());
            ps.setBoolean(3, r.approved());
            ps.setInt(4, r.commentCount());
            ps.setObject(5, now);
        });
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= MAX_TEXT ? s : s.substring(0, MAX_TEXT);
    }

    /** Композитный ключ для map существующих MR (натуральный ключ GitLab). */
    private record MrKey(long projectId, long iid) {}

    /** Денормализованная строка ревью для native batch insert. */
    private record ReviewRow(long mrId, String reviewerEmail, boolean approved, int commentCount) {}
}
