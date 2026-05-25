package ru.x5.markable.dev.analytics.adapter.rest.dto;

import java.time.LocalDateTime;
import java.util.List;
import ru.x5.markable.dev.analytics.domain.model.kaiten.KaitenCard;

/** Карточка Kaiten для REST-ответов. */
public record KaitenCardResponse(
        long id,
        String title,
        String description,
        String status,
        String columnName,
        String boardName,
        String spaceName,
        Long ownerId,
        String ownerName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime closedAt,
        boolean archived,
        String url,
        List<Long> memberIds
) {
    public static KaitenCardResponse from(KaitenCard c) {
        return new KaitenCardResponse(
                c.id().value(),
                c.title(),
                c.description(),
                c.status(),
                c.columnName(),
                c.boardName(),
                c.spaceName(),
                c.ownerId() == null ? null : c.ownerId().value(),
                c.ownerName(),
                c.createdAt(),
                c.updatedAt(),
                c.closedAt(),
                c.archived(),
                c.url(),
                c.memberIds().stream().map(m -> m.value()).toList());
    }
}
