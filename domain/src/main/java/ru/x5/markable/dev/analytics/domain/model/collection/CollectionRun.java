package ru.x5.markable.dev.analytics.domain.model.collection;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Запись о запуске сбора статистики.
 *
 * <p>Хранится для аудита: когда стартовали, какой период покрывает, чем закончилось.
 * При перезапуске неудачного интервала следующий run автоматически подхватит
 * последний {@code SUCCESS}-period как стартовую точку.</p>
 */
public record CollectionRun(
        UUID id,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime sinceDate,
        LocalDateTime untilDate,
        CollectionStatus status,
        String errorMessage
) {

    public CollectionRun {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(startedAt, "startedAt required");
        Objects.requireNonNull(sinceDate, "sinceDate required");
        Objects.requireNonNull(untilDate, "untilDate required");
        Objects.requireNonNull(status, "status required");
    }

    /** Создать новый run в статусе {@link CollectionStatus#RUNNING}. */
    public static CollectionRun start(LocalDateTime since, LocalDateTime until) {
        return new CollectionRun(
                UUID.randomUUID(),
                LocalDateTime.now(),
                null,
                since,
                until,
                CollectionStatus.RUNNING,
                null
        );
    }

    public CollectionRun succeeded() {
        return new CollectionRun(id, startedAt, LocalDateTime.now(), sinceDate, untilDate,
                CollectionStatus.SUCCESS, null);
    }

    public CollectionRun failed(String error) {
        return new CollectionRun(id, startedAt, LocalDateTime.now(), sinceDate, untilDate,
                CollectionStatus.FAILED, error);
    }

    public Optional<String> error() {
        return Optional.ofNullable(errorMessage);
    }
}
