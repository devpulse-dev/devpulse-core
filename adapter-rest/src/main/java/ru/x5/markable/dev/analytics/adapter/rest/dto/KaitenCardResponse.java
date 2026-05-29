package ru.x5.markable.dev.analytics.adapter.rest.dto;

import java.time.LocalDateTime;
import java.util.List;
import ru.x5.markable.dev.analytics.domain.model.kaiten.KaitenCard;

/**
 * Карточка Kaiten для REST-ответов.
 *
 * <p><b>Тип карточки:</b> {@code typeId} — сырой id из Kaiten, {@code cardType} — наша
 * классификация ({@code DEVELOPMENT} / {@code DEFECT} / {@code OTHER}).</p>
 *
 * <p><b>Статус карточки:</b> вычисляется из колонки, в которой карточка сейчас находится:
 * {@code columnType} — сырой код (1/2/3), {@code columnStatus} — наша категория
 * ({@code NEW} / {@code IN_PROGRESS} / {@code DONE} / {@code UNKNOWN}). {@code columnTitle} —
 * человекочитаемое название колонки на доске («В уточнении», «Готово к ревью» и т.п.).</p>
 *
 * <p>Поле {@code archived} — это просто «карточка скрыта на доске», НЕ статус задачи.
 * Завершённой карточка считается когда {@code columnStatus == DONE} (или {@code closedAt != null}).</p>
 */
public record KaitenCardResponse(
        long id,
        String title,
        String description,
        Integer typeId,
        String cardType,
        Integer columnType,
        String columnStatus,
        String columnTitle,
        String boardName,
        String spaceName,
        Long ownerId,
        String ownerName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime closedAt,
        boolean archived,
        boolean closed,
        String url,
        List<Long> memberIds
) {
    public static KaitenCardResponse from(KaitenCard c) {
        return new KaitenCardResponse(
                c.id().value(),
                c.title(),
                c.description(),
                c.typeId(),
                c.cardType().name(),
                c.columnType(),
                c.columnStatus().name(),
                c.columnTitle(),
                c.boardName(),
                c.spaceName(),
                c.ownerId() == null ? null : c.ownerId().value(),
                c.ownerName(),
                c.createdAt(),
                c.updatedAt(),
                c.closedAt(),
                c.archived(),
                c.isClosed(),
                c.url(),
                c.memberIds().stream().map(m -> m.value()).toList());
    }
}
