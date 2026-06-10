package ru.x5.devpulse.application.port.in;

import java.util.Optional;
import java.util.UUID;
import ru.x5.devpulse.domain.model.collection.CollectionRun;

/**
 * Чтение записей прогона сбора (опрос статуса).
 * Обслуживает {@code GET /api/v2/collection/runs/{id}} и {@code .../runs/latest}.
 */
public interface GetCollectionRunUseCase {

    Optional<CollectionRun> findById(UUID id);

    /**
     * Самый свежий прогон (идущий, если есть). Источник правды для фронта по id живого
     * прогона. Обслуживает {@code GET /api/v2/collection/runs/latest}.
     */
    Optional<CollectionRun> findLatest();
}
