package ru.x5.devpulse.domain.model.kaiten;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import ru.x5.devpulse.domain.model.user.KaitenUserId;

/**
 * Карточка задачи в Kaiten.
 *
 * <p><b>Статус</b> карточки определяется {@code column.type} ({@link KaitenColumnStatus#fromType}),
 * а не {@code archived} (archived = карточка скрыта на доске, она может быть и в работе, и завершена).
 * Поле {@code columnTitle} — человекочитаемое название колонки («В уточнении», «Готово к ревью»),
 * подходит для отображения; {@code columnStatus} — программная категория NEW/IN_PROGRESS/DONE.</p>
 *
 * <p><b>Тип карточки</b> ({@link KaitenCardType}) — DEVELOPMENT / DEFECT / OTHER — берётся из
 * {@code type_id} API. Список известных типов в {@link KaitenCardType}.</p>
 */
public record KaitenCard(
        KaitenCardId id,
        String title,
        String description,
        Integer typeId,
        Integer columnType,
        String columnTitle,
        String boardName,
        String spaceName,
        KaitenUserId ownerId,
        String ownerName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime closedAt,
        boolean archived,
        String url,
        List<KaitenUserId> memberIds
) {

    public KaitenCard {
        Objects.requireNonNull(id, "card id required");
        memberIds = memberIds == null ? List.of() : List.copyOf(memberIds);
    }

    /** Производная: классифицированный тип карточки (DEVELOPMENT / DEFECT / OTHER). */
    public KaitenCardType cardType() {
        return KaitenCardType.fromId(typeId);
    }

    /** Производная: статус по {@code column.type} (NEW / IN_PROGRESS / DONE / UNKNOWN). */
    public KaitenColumnStatus columnStatus() {
        return KaitenColumnStatus.fromType(columnType);
    }

    public boolean isClosed() {
        return columnStatus() == KaitenColumnStatus.DONE || closedAt != null;
    }
}
