package ru.x5.devpulse.adapter.persistence.review;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
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
 */
@Component
@Log4j2
@RequiredArgsConstructor
class ReviewWriteRepositoryAdapter implements ReviewWriteRepository {

    private static final int MAX_TEXT = 1000;
    private static final int CHUNK = 500;

    private final MergeRequestJpaRepository mrJpa;
    private final MrReviewJpaRepository reviewJpa;

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
        List<MergeRequestEntity> entities = new ArrayList<>(chunk.size());
        List<Long> existingMrIds = new ArrayList<>();

        for (CollectedMergeRequest c : chunk) {
            MergeRequestEntity mr = mrJpa
                    .findByGitlabProjectIdAndGitlabMrIid(c.gitlabProjectId(), c.gitlabMrIid())
                    .orElseGet(MergeRequestEntity::new);
            if (mr.getId() != null) existingMrIds.add(mr.getId()); // существующий → удалить его старые ревью

            mr.setGitlabProjectId(c.gitlabProjectId());
            mr.setGitlabMrIid(c.gitlabMrIid());
            mr.setAuthorEmail(c.author().value());
            mr.setTitle(truncate(c.title()));
            mr.setWebUrl(truncate(c.webUrl()));
            mr.setState(c.state() == null || c.state().isBlank() ? "unknown" : c.state());
            mr.setCreatedAt(c.createdAt());
            mr.setMergedAt(c.mergedAt());
            mr.setCollectedAt(now);
            entities.add(mrJpa.save(mr)); // IDENTITY → insert сразу, id назначен
        }

        // Replace ревью: один bulk-delete на весь чанк (только для существовавших MR), затем вставка.
        if (!existingMrIds.isEmpty()) {
            reviewJpa.deleteByMergeRequestIdIn(existingMrIds);
        }

        List<MrReviewEntity> rows = new ArrayList<>();
        for (int i = 0; i < chunk.size(); i++) {
            Long mrId = entities.get(i).getId();
            for (MrReview r : chunk.get(i).reviews()) {
                rows.add(MrReviewEntity.builder()
                        .mergeRequestId(mrId)
                        .reviewerEmail(r.reviewer().value())
                        .approved(r.approved())
                        .commentCount(r.commentCount())
                        .collectedAt(now)
                        .build());
            }
        }
        reviewJpa.saveAll(rows);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= MAX_TEXT ? s : s.substring(0, MAX_TEXT);
    }
}
