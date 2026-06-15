package ru.x5.devpulse.adapter.auth;

import java.security.Principal;
import ru.x5.devpulse.domain.model.user.Role;

/**
 * Principal аутентифицированного пользователя в сессии (ADR-13). Хранит то, что нужно фронту
 * и RBAC: email, роль и профиль. {@code getName()} = email (имя principal для Spring Session).
 */
public record DevpulsePrincipal(
        String email,
        Role role,
        String name,
        String avatarUrl,
        String team
) implements Principal {

    @Override
    public String getName() {
        return email;
    }
}
