package ru.x5.markable.dev.analytics.adapter.rest.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import ru.x5.markable.dev.analytics.domain.model.collection.CollectionRun;

/** Запись прогона сбора в REST-ответах. */
public record CollectionRunResponse(
        UUID id,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime sinceDate,
        LocalDateTime untilDate,
        String status,
        String errorMessage
) {
    public static CollectionRunResponse from(CollectionRun r) {
        return new CollectionRunResponse(
                r.id(),
                r.startedAt(),
                r.finishedAt(),
                r.sinceDate(),
                r.untilDate(),
                r.status().name(),
                r.errorMessage());
    }
}
