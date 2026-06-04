package ru.x5.devpulse.adapter.persistence.review;

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
 */
@Component
@Log4j2
@RequiredArgsConstructor
class ReviewWriteRepositoryAdapter implements ReviewWriteRepository {

    private static final int MAX_TEXT = 1000;

    private final MergeRequestJpaRepository mrJpa;
    private final MrReviewJpaRepository reviewJpa;

    @Override
    @Transactional
    public void upsert(List<CollectedMergeRequest> mergeRequests) {
        if (mergeRequests == null || mergeRequests.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();

        for (CollectedMergeRequest c : mergeRequests) {
            MergeRequestEntity mr = mrJpa
                    .findByGitlabProjectIdAndGitlabMrIid(c.gitlabProjectId(), c.gitlabMrIid())
                    .orElseGet(MergeRequestEntity::new);

            mr.setGitlabProjectId(c.gitlabProjectId());
            mr.setGitlabMrIid(c.gitlabMrIid());
            mr.setAuthorEmail(c.author().value());
            mr.setTitle(truncate(c.title()));
            mr.setWebUrl(truncate(c.webUrl()));
            mr.setState(c.state() == null || c.state().isBlank() ? "unknown" : c.state());
            mr.setCreatedAt(c.createdAt());
            mr.setMergedAt(c.mergedAt());
            mr.setCollectedAt(now);
            MergeRequestEntity saved = mrJpa.save(mr);

            // Replace ревью: удалить старые, вставить актуальные.
            reviewJpa.deleteByMergeRequestId(saved.getId());
            reviewJpa.flush(); // до insert — иначе uniq (mr_id, reviewer_email) может конфликтовать

            List<MrReviewEntity> rows = new ArrayList<>(c.reviews().size());
            for (MrReview r : c.reviews()) {
                rows.add(MrReviewEntity.builder()
                        .mergeRequestId(saved.getId())
                        .reviewerEmail(r.reviewer().value())
                        .approved(r.approved())
                        .commentCount(r.commentCount())
                        .collectedAt(now)
                        .build());
            }
            reviewJpa.saveAll(rows);
        }
        log.info("Записано/обновлено {} MR с ревью", mergeRequests.size());
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= MAX_TEXT ? s : s.substring(0, MAX_TEXT);
    }
}
