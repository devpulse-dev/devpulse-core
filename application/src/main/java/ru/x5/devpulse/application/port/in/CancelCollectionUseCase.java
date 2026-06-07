package ru.x5.devpulse.application.port.in;

import java.util.Optional;
import java.util.UUID;
import ru.x5.devpulse.domain.model.collection.CollectionRun;

/**
 * Отмена идущего прогона сбора (POST /collection/runs/{id}/cancel).
 *
 * <p>Кооперативная: ставит флаг отмены, сам сбор остановится на ближайшем checkpoint'е.</p>
 */
public interface CancelCollectionUseCase {

    /**
     * Запрашивает отмену прогона {@code runId}.
     *
     * @return прогон (в статусе RUNNING с поднятым флагом отмены), если он существует;
     *         {@link Optional#empty()} — прогона с таким id нет (→ 404)
     * @throws ru.x5.devpulse.application.port.out.CollectionRunNotCancellableException
     *         если прогон уже в терминальном статусе (→ 409)
     */
    Optional<CollectionRun> cancel(UUID runId);
}
