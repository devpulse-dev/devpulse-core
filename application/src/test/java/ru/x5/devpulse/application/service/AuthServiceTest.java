package ru.x5.devpulse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.x5.devpulse.application.port.out.GitIdentityProvider;
import ru.x5.devpulse.application.port.out.ProjectAccessDeniedException;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.model.user.AuthenticatedUser;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.GitIdentity;
import ru.x5.devpulse.domain.model.user.GitTokenType;
import ru.x5.devpulse.domain.model.user.Role;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService (ADR-13: identity → гейт доступа → провижининг → роль)")
class AuthServiceTest {

    private static final int DEVELOPER = 30;

    @Mock GitIdentityProvider gitIdentityProvider;
    @Mock UnifiedUserRepository unifiedUserRepository;

    private AuthService service(Set<Email> admins) {
        return new AuthService(gitIdentityProvider, unifiedUserRepository, admins);
    }

    @Test
    @DisplayName("Админ из конфига обходит project-access гейт и получает роль ADMIN")
    void adminBypassesAccessGate() {
        Email admin = new Email("admin@x5.ru");
        GitIdentity id = identity(1, admin);
        when(gitIdentityProvider.fetchIdentity("tok", GitTokenType.PAT)).thenReturn(id);
        when(unifiedUserRepository.provision(id)).thenReturn(user(admin, false));

        AuthenticatedUser result = service(Set.of(admin)).authenticate("tok", GitTokenType.PAT);

        assertAll("admin bypass",
                () -> assertThat(result.role()).isEqualTo(Role.ADMIN),
                () -> assertThat(result.email()).isEqualTo(admin),
                // ключевое: гейт доступа к проектам для админа не дёргается вовсе
                () -> verify(gitIdentityProvider, never()).maxProjectAccessLevel(anyInt()));
    }

    @Test
    @DisplayName("Не-админ с Developer-доступом (≥30) провижинится как MEMBER")
    void developerAccessProvisionsMember() {
        Email dev = new Email("dev@x5.ru");
        GitIdentity id = identity(2, dev);
        when(gitIdentityProvider.fetchIdentity(any(), any())).thenReturn(id);
        when(gitIdentityProvider.maxProjectAccessLevel(2)).thenReturn(DEVELOPER);
        when(unifiedUserRepository.provision(id)).thenReturn(user(dev, false));

        AuthenticatedUser result = service(Set.of()).authenticate("tok", GitTokenType.PAT);

        assertThat(result.role()).isEqualTo(Role.MEMBER);
    }

    @Test
    @DisplayName("Не-админ БЕЗ Developer-доступа (<30) → ProjectAccessDenied, провижининг не выполняется")
    void belowDeveloperDenied() {
        Email guest = new Email("guest@x5.ru");
        GitIdentity id = identity(3, guest);
        when(gitIdentityProvider.fetchIdentity(any(), any())).thenReturn(id);
        when(gitIdentityProvider.maxProjectAccessLevel(3)).thenReturn(DEVELOPER - 10); // Reporter

        AuthService svc = service(Set.of());
        assertThatThrownBy(() -> svc.authenticate("tok", GitTokenType.PAT))
                .isInstanceOf(ProjectAccessDeniedException.class);
        verify(unifiedUserRepository, never()).provision(any());
    }

    @Test
    @DisplayName("Не-админ с is_lead → роль TEAMLEAD")
    void leadResolvesToTeamlead() {
        Email lead = new Email("lead@x5.ru");
        GitIdentity id = identity(4, lead);
        when(gitIdentityProvider.fetchIdentity(any(), any())).thenReturn(id);
        when(gitIdentityProvider.maxProjectAccessLevel(4)).thenReturn(40); // Maintainer
        when(unifiedUserRepository.provision(id)).thenReturn(user(lead, true));

        assertThat(service(Set.of()).authenticate("tok", GitTokenType.PAT).role())
                .isEqualTo(Role.TEAMLEAD);
    }

    private static GitIdentity identity(int gitlabId, Email email) {
        return new GitIdentity(gitlabId, email, email.localPart(), "Name", null);
    }

    private static UnifiedUser user(Email email, boolean lead) {
        LocalDateTime now = LocalDateTime.now();
        return new UnifiedUser(1L, email, email.localPart(), "Name", null,
                null, null, null, lead, now, now, now);
    }
}
