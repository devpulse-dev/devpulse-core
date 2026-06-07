package ru.x5.devpulse.adapter.persistence.review;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
 * <p><b>Производительность.</b> Бэкфилл может приносить десятки тысяч MR за раз. Пишем
 * чанками по {@link #CHUNK} с {@code flush()+clear()} после каждого — иначе persistence-context
 * растёт и dirty-checking на каждом flush становится O(n²) (минуты на 20k+ MR). Старые ревью
 * чанка удаляются одним bulk-запросом, не по одному MR.</p>
 *
 * <p><b>P1-3.</b> Существующие MR находятся одним batch-запросом на чанк (вместо N+1
 * {@code findBy} per MR). Сами строки {@code mr_review} пишутся через native
 * {@link JdbcTemplate#batchUpdate} — {@code MrReviewEntity} на {@code IDENTITY}, и
 * {@code reviewJpa.saveAll} не батчился (та же причина, что в ADR-11 для commit_details).</p>
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

    private final MergeRequestJpaRepository mrJpa;
    private final MrReviewJpaRepository reviewJpa;
    private final JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public void upsert(List<CollectedMergeRequest> mergeRequests) {
        if (mergeRequests == null || mergeRequests.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        int total = mergeRequests.size();

        for (int start = 0; start < total; start += CHUNK) {
            int end = Math.min(start + CHUNK, total);
            upsertChunk(mergeRequests.subList(start, end), now);
            em.flush();
            em.clear(); // отцепляем сущности чанка — контекст не растёт, dirty-checking остаётся O(chunk)
            log.info("Записано {}/{} MR с ревью", end, total);
        }
        log.info("Записано/обновлено {} MR с ревью", total);
    }

    private void upsertChunk(List<CollectedMergeRequest> chunk, LocalDateTime now) {
        // 1. Один batch-lookup существующих MR (вместо N+1 findBy per MR).
        Set<Long> projectIds = new HashSet<>();
        Set<Long> iids = new HashSet<>();
        for (CollectedMergeRequest c : chunk) {
            projectIds.add(c.gitlabProjectId());
            iids.add(c.gitlabMrIid());
        }
        Map<MrKey, MergeRequestEntity> existing = new HashMap<>();
        for (MergeRequestEntity e : mrJpa.findByGitlabProjectIdInAndGitlabMrIidIn(projectIds, iids)) {
            existing.put(new MrKey(e.getGitlabProjectId(), e.getGitlabMrIid()), e);
        }

        List<Long> existingMrIds = new ArrayList<>();
        List<ReviewRow> reviewRows = new ArrayList<>();

        for (CollectedMergeRequest c : chunk) {
            MergeRequestEntity mr = existing.get(new MrKey(c.gitlabProjectId(), c.gitlabMrIid()));
            if (mr == null) {
                mr = new MergeRequestEntity();
            } else {
                existingMrIds.add(mr.getId()); // существующий → его старые ревью заменим
            }

            mr.setGitlabProjectId(c.gitlabProjectId());
            mr.setGitlabMrIid(c.gitlabMrIid());
            mr.setAuthorEmail(c.author().value());
            mr.setTitle(truncate(c.title()));
            mr.setWebUrl(truncate(c.webUrl()));
            mr.setState(c.state() == null || c.state().isBlank() ? "unknown" : c.state());
            mr.setCreatedAt(c.createdAt());
            mr.setMergedAt(c.mergedAt());
            mr.setCollectedAt(now);
            Long mrId = mrJpa.save(mr).getId(); // IDENTITY → insert/flush, id назначен

            for (MrReview r : c.reviews()) {
                reviewRows.add(new ReviewRow(
                        mrId, r.reviewer().value(), r.approved(), r.commentCount()));
            }
        }

        // 2. Replace ревью: bulk-delete старых (только для существовавших MR), затем native insert.
        //    deleteByMergeRequestIdIn (JPQL) триггерит auto-flush апдейтов MR перед удалением;
        //    новые MR с IDENTITY уже зафлашены в save() — строки в БД, FK mr_review удовлетворён.
        if (!existingMrIds.isEmpty()) {
            reviewJpa.deleteByMergeRequestIdIn(existingMrIds);
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
