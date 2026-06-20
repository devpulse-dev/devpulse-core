package ru.x5.devpulse.adapter.auth;

import java.security.Principal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import ru.x5.devpulse.domain.model.user.Role;

/**
 * Principal аутентифицированного пользователя в сессии (ADR-13) — единый для обоих входов:
 * PAT (хранится в {@code UsernamePasswordAuthenticationToken}) и OAuth2 (возвращается
 * {@code GitlabOAuth2UserService}, поэтому реализует {@link OAuth2User}). Хранит то, что нужно
 * фронту и RBAC: email, роль, профиль. {@code getName()} = email.
 */
public record DevpulsePrincipal(
        String email,
        Role role,
        String name,
        String avatarUrl,
        String team
) implements Principal, OAuth2User {

    @Override
    public String getName() {
        return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /** Атрибуты OAuth2 нам не нужны — профиль читается из полей record'а. */
    @Override
    public Map<String, Object> getAttributes() {
        return Map.of();
    }
}
