package ru.x5.devpulse.application.service;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import ru.x5.devpulse.application.port.in.AuthenticateUseCase;
import ru.x5.devpulse.application.port.out.GitIdentityProvider;
import ru.x5.devpulse.application.port.out.ProjectAccessDeniedException;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.model.user.AuthenticatedUser;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.GitIdentity;
import ru.x5.devpulse.domain.model.user.GitTokenType;
import ru.x5.devpulse.domain.model.user.Role;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

/**
 * Аутентификация (см. ADR-13): identity из GitLab → проверка доступа к проектам → провижининг
 * → резолв роли. Spring-free: {@code adminEmails} инжектится из bootstrap-конфига ({@code
 * auth.admins}), уже в lower-case (через {@link Email}).
 */
@RequiredArgsConstructor
public final class AuthService implements AuthenticateUseCase {

    /** GitLab access_level «Developer» — минимум для входа не-админу. */
    private static final int DEVELOPER = 30;

    private final GitIdentityProvider gitIdentityProvider;
    private final UnifiedUserRepository unifiedUserRepository;
    private final Set<Email> adminEmails;

    @Override
    public AuthenticatedUser authenticate(String token, GitTokenType type) {
        GitIdentity identity = gitIdentityProvider.fetchIdentity(token, type);

        boolean admin = adminEmails.contains(identity.email());
        // Админы из конфига обходят project-access гейт (см. ADR-13); identity всё равно валидна.
        if (!admin && gitIdentityProvider.maxProjectAccessLevel(identity.gitlabId()) < DEVELOPER) {
            throw new ProjectAccessDeniedException(
                    "No Developer access to tracked projects: " + identity.email().value());
        }

        UnifiedUser user = unifiedUserRepository.provision(identity);
        Role role = Role.resolve(admin, user.lead());
        return new AuthenticatedUser(user.email(), role, user.name(), user.avatarUrl(), user.team());
    }
}
