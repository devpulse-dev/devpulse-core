package ru.x5.devpulse.domain.model.user;

/**
 * Аутентифицированный пользователь — результат use-case аутентификации (см. ADR-13): то, что
 * REST отдаёт фронту ({@code GET /auth/me}) и кладёт в principal сессии. Зависит только от
 * доменных типов ({@link Email}, {@link Role}). {@code name}/{@code avatarUrl}/{@code team}
 * могут быть null.
 */
public record AuthenticatedUser(
        Email email,
        Role role,
        String name,
        String avatarUrl,
        String team
) {}
