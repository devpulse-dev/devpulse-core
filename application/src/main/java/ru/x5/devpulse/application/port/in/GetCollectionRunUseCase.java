package ru.x5.devpulse.application.port.in;

import java.util.Optional;
import java.util.UUID;
import ru.x5.devpulse.domain.model.collection.CollectionRun;

/**
 * Возвращает запись прогона сбора по {@code id} для опроса статуса.
 * Обслуживает {@code GET /api/v2/collection/runs/{id}}.
 */
public interface GetCollectionRunUseCase {
    Optional<CollectionRun> findById(UUID id);
}
