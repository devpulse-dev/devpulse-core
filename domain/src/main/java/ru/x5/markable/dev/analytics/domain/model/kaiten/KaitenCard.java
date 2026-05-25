package ru.x5.markable.dev.analytics.domain.model.kaiten;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import ru.x5.markable.dev.analytics.domain.model.user.KaitenUserId;

/**
 * Карточка задачи в Kaiten.
 *
 * <p>Список {@link #memberIds() участников} — иммутабельный snapshot на момент синхронизации.
 * Если карточка появилась без участников — здесь будет пустой список, не {@code null}.</p>
 */
public record KaitenCard(
        KaitenCardId id,
        String title,
        String description,
        String status,
        String columnName,
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

    public boolean isClosed() {
        return closedAt != null;
    }
}
