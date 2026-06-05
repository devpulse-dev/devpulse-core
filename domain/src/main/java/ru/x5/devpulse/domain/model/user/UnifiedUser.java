package ru.x5.devpulse.domain.model.user;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Унифицированный пользователь, объединяющий идентичности из разных систем (Git author + Kaiten user).
 *
 * <p>Ключ — {@link Email}. Опциональные поля обернуты в {@link Optional} через геттеры,
 * чтобы вызывающий код не получал {@code null} наружу.</p>
 */
public record UnifiedUser(
        Long id,
        Email email,
        String username,
        String name,
        String avatarUrl,
        KaitenUserId kaitenId,
        Integer gitlabId,
        String team,
        boolean lead,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastSyncedAt
) {

    public Optional<String> displayName() {
        return Optional.ofNullable(name);
    }

    public Optional<String> avatar() {
        return Optional.ofNullable(avatarUrl);
    }

    public Optional<KaitenUserId> kaiten() {
        return Optional.ofNullable(kaitenId);
    }

    /** Команда пользователя (назначается через фронт). {@code null}, если не задана. */
    public Optional<String> teamName() {
        return Optional.ofNullable(team);
    }
}
