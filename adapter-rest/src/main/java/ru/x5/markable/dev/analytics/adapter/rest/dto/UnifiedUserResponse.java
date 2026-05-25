package ru.x5.markable.dev.analytics.adapter.rest.dto;

import ru.x5.markable.dev.analytics.domain.model.user.UnifiedUser;

/** Унифицированный пользователь для REST-ответов. */
public record UnifiedUserResponse(
        Long id,
        String email,
        String username,
        String name,
        String avatarUrl,
        Long kaitenId,
        Integer gitlabId
) {
    public static UnifiedUserResponse from(UnifiedUser u) {
        return new UnifiedUserResponse(
                u.id(),
                u.email().value(),
                u.username(),
                u.name(),
                u.avatarUrl(),
                u.kaiten().map(k -> k.value()).orElse(null),
                u.gitlabId());
    }
}
