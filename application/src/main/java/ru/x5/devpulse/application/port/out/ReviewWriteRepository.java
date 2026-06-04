package ru.x5.devpulse.application.port.out;

import java.util.List;
import ru.x5.devpulse.domain.model.review.CollectedMergeRequest;

/**
 * Port out: запись собранных MR и участия ревьюеров.
 *
 * <p>Идемпотентно: upsert MR по ключу {@code (gitlab_project_id, gitlab_mr_iid)},
 * ревью пересобираются (replace) — повторный сбор того же MR не плодит дублей.</p>
 */
public interface ReviewWriteRepository {

    void upsert(List<CollectedMergeRequest> mergeRequests);
}
