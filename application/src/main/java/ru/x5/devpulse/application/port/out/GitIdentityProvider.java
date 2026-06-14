package ru.x5.devpulse.application.port.out;

import ru.x5.devpulse.domain.model.user.GitIdentity;
import ru.x5.devpulse.domain.model.user.GitTokenType;

/**
 * Port out: идентичность и доступы пользователя в GitLab. Реализация — {@code adapter-reviews}
 * поверх {@code GitlabHttpClient} (см. ADR-13).
 */
public interface GitIdentityProvider {

    /**
     * Идентичность по токену <b>пользователя</b> ({@code GET /user}) — токен доказывает владение.
     *
     * @param type PAT (заголовок {@code PRIVATE-TOKEN}) или OAUTH ({@code Authorization: Bearer})
     * @throws InvalidGitTokenException токен невалиден/отозван (GitLab вернул 401)
     */
    GitIdentity fetchIdentity(String token, GitTokenType type);

    /**
     * Максимальный {@code access_level} пользователя среди отслеживаемых проектов
     * ({@code gitlab.api.projects}), считается <b>сервисным</b> токеном. {@code 0} — ни в одном.
     *
     * <p>Уровни GitLab: 10 Guest, 20 Reporter, 30 Developer, 40 Maintainer, 50 Owner.</p>
     */
    int maxProjectAccessLevel(int gitlabUserId);
}
