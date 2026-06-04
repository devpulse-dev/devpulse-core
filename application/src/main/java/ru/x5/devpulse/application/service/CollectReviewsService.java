package ru.x5.devpulse.application.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.x5.devpulse.application.port.in.CollectReviewsUseCase;
import ru.x5.devpulse.application.port.out.ReviewGateway;
import ru.x5.devpulse.application.port.out.ReviewWriteRepository;
import ru.x5.devpulse.domain.model.review.CollectedMergeRequest;

/**
 * Реализация {@link CollectReviewsUseCase}: GitLab → БД.
 *
 * <ol>
 *   <li>{@code reviewGateway.fetchMergeRequests(since)} — MR/approvals/notes из GitLab,
 *       email'ы ревьюеров уже разрезолвлены;</li>
 *   <li>{@code reviewWriteRepository.upsert(...)} — идемпотентная запись.</li>
 * </ol>
 *
 * <p>Без I/O напрямую — только координация порта-сбора и порта-записи (как остальные
 * collection-фазы). Изоляцию (падение не валит прогон) обеспечивает оркестратор.</p>
 */
@Slf4j
@RequiredArgsConstructor
public final class CollectReviewsService implements CollectReviewsUseCase {

    private final ReviewGateway reviewGateway;
    private final ReviewWriteRepository reviewWriteRepository;

    @Override
    public void collect(LocalDateTime since) {
        log.info("Старт сбора ревью-метрик из GitLab (updated_after={})", since);
        List<CollectedMergeRequest> mrs = reviewGateway.fetchMergeRequests(since);
        if (mrs.isEmpty()) {
            log.info("GitLab не вернул MR за период — нечего писать");
            return;
        }
        reviewWriteRepository.upsert(mrs);
        log.info("Сбор ревью завершён: {} MR записано/обновлено", mrs.size());
    }
}
