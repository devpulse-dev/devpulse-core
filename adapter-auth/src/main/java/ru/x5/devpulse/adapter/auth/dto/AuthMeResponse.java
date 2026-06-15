package ru.x5.devpulse.adapter.auth.dto;

import ru.x5.devpulse.domain.model.user.AuthenticatedUser;

/**
 * Ответ {@code POST /auth/login} и {@code GET /auth/me}: текущий пользователь + роль для фронта.
 * {@code role} — строка ({@code MEMBER|TEAMLEAD|ADMIN}).
 */
public record AuthMeResponse(
        String email,
        String role,
        String name,
        String avatarUrl,
        String team
) {
    public static AuthMeResponse from(AuthenticatedUser u) {
        return new AuthMeResponse(
                u.email().value(), u.role().name(), u.name(), u.avatarUrl(), u.team());
    }
}
