package ru.x5.devpulse.application.port.out;

import java.util.UUID;
import ru.x5.devpulse.domain.model.collection.CollectionStatus;

/**
 * Бросается при попытке отменить прогон, который уже в терминальном статусе
 * (SUCCESS / FAILED / CANCELLED). В REST-слое маппится в {@code HTTP 409 Conflict}.
 */
public class CollectionRunNotCancellableException extends RuntimeException {

    public CollectionRunNotCancellableException(UUID runId, CollectionStatus status) {
        super("Прогон " + runId + " нельзя отменить — он в терминальном статусе " + status);
    }
}
