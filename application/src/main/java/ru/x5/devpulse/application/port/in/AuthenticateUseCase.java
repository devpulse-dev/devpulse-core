package ru.x5.devpulse.application.port.in;

import ru.x5.devpulse.domain.model.user.AuthenticatedUser;
import ru.x5.devpulse.domain.model.user.GitTokenType;

/**
 * Аутентификация по токену GitLab (PAT или OAuth access-token). Обслуживает оба входа
 * (см. ADR-13): {@code POST /auth/login} (PAT) и OAuth2-success-хендлер.
 *
 * <p>Шаги: identity ({@code GET /user}) → проверка доступа к отслеживаемым проектам (или
 * членство в {@code auth.admins}) → провижининг {@code unified_user} → резолв роли.</p>
 */
public interface AuthenticateUseCase {

    /**
     * @param token GitLab-токен пользователя
     * @param type  PAT или OAUTH
     * @throws ru.x5.devpulse.application.port.out.InvalidGitTokenException токен невалиден → 401
     * @throws ru.x5.devpulse.application.port.out.ProjectAccessDeniedException нет доступа ≥ Developer и не админ → 403
     */
    AuthenticatedUser authenticate(String token, GitTokenType type);
}
