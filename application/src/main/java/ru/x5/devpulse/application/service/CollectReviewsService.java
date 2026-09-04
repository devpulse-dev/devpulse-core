package ru.x5.devpulse.application.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.x5.devpulse.application.port.in.CancellationSignal;
import ru.x5.devpulse.application.port.in.CollectReviewsUseCase;
import ru.x5.devpulse.application.port.out.ReviewGateway;
import ru.x5.devpulse.application.port.out.ReviewWriteRepository;

/**
 * Реализация {@link CollectReviewsUseCase}: GitLab → БД.
 *
 * <ol>
 *   <li>{@code reviewGateway.streamMergeRequests(since, ...)} — MR/approvals/notes из GitLab
 *       чанками, email'ы ревьюеров уже разрезолвлены;</li>
 *   <li>{@code reviewWriteRepository.upsert(batch)} — идемпотентная запись чанка.</li>
 * </ol>
 *
 * <p>Чанк меньше проекта и приходит по ходу сбора, поэтому запись идёт много раз за проект:
 * собранное закрепляется в БД сразу, и сбой на середине крупного проекта не обнуляет
 * десятки минут работы. Весь корпус MR в heap не копится (на бэкфилле за год это OOM-риск).
 * Без I/O напрямую — только координация порта-сбора и порта-записи. Изоляцию (падение не валит
 * прогон) обеспечивает оркестратор.</p>
 */
@Slf4j
@RequiredArgsConstructor
public final class CollectReviewsService implements CollectReviewsUseCase {

    private final ReviewGateway reviewGateway;
    private final ReviewWriteRepository reviewWriteRepository;

    @Override
    public void collect(LocalDateTime since, CancellationSignal cancel) {
        log.info("Старт сбора ревью-метрик из GitLab (updated_after={})", since);
        int[] total = {0};
        reviewGateway.streamMergeRequests(since, cancel::cancelled, batch -> {
            if (batch.isEmpty()) {
                return;
            }
            reviewWriteRepository.upsert(batch);
            total[0] += batch.size();
        });
        if (total[0] == 0) {
            log.info("GitLab не вернул MR за период — нечего писать");
            return;
        }
        log.info("Сбор ревью завершён: {} MR записано/обновлено", total[0]);
    }
}
