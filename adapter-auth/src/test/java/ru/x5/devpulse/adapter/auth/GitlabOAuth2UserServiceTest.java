package ru.x5.devpulse.adapter.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import ru.x5.devpulse.application.port.in.AuthenticateUseCase;
import ru.x5.devpulse.application.port.out.GitUnavailableException;
import ru.x5.devpulse.application.port.out.InvalidGitTokenException;
import ru.x5.devpulse.application.port.out.ProjectAccessDeniedException;
import ru.x5.devpulse.domain.model.user.AuthenticatedUser;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.GitTokenType;
import ru.x5.devpulse.domain.model.user.Role;

@ExtendWith(MockitoExtension.class)
@DisplayName("GitlabOAuth2UserService (маппинг ошибок аутентификации в OAuth2Error)")
class GitlabOAuth2UserServiceTest {

    @Mock AuthenticateUseCase authenticate;
    @Mock OAuth2UserRequest userRequest;

    private GitlabOAuth2UserService service() {
        OAuth2AccessToken token = mock(OAuth2AccessToken.class);
        when(token.getTokenValue()).thenReturn("oauth-tok");
        when(userRequest.getAccessToken()).thenReturn(token);
        return new GitlabOAuth2UserService(authenticate);
    }

    @Test
    @DisplayName("Успех: вход OAUTH-токеном → DevpulsePrincipal с email/ролью")
    void successReturnsPrincipal() {
        var svc = service();
        when(authenticate.authenticate("oauth-tok", GitTokenType.OAUTH)).thenReturn(
                new AuthenticatedUser(new Email("boris@x5.ru"), Role.MEMBER, "Boris", null, "Platform"));

        OAuth2User user = svc.loadUser(userRequest);

        assertThat(user).isInstanceOf(DevpulsePrincipal.class);
        assertThat(user.getName()).as("getName() == email").isEqualTo("boris@x5.ru");
    }

    @Test
    @DisplayName("Нет Developer-доступа → OAuth2AuthenticationException access_denied")
    void accessDeniedMapsToOAuthError() {
        var svc = service();
        when(authenticate.authenticate(any(), eq(GitTokenType.OAUTH)))
                .thenThrow(new ProjectAccessDeniedException("нет доступа"));

        assertOAuthErrorCode(() -> svc.loadUser(userRequest), "access_denied");
    }

    @Test
    @DisplayName("Невалидный токен → OAuth2AuthenticationException invalid_token")
    void invalidTokenMapsToOAuthError() {
        var svc = service();
        when(authenticate.authenticate(any(), any()))
                .thenThrow(new InvalidGitTokenException("токен отклонён"));

        assertOAuthErrorCode(() -> svc.loadUser(userRequest), "invalid_token");
    }

    @Test
    @DisplayName("GitLab недоступен → OAuth2AuthenticationException server_error")
    void unavailableMapsToOAuthError() {
        var svc = service();
        when(authenticate.authenticate(any(), any()))
                .thenThrow(new GitUnavailableException("GitLab недоступен", new RuntimeException()));

        assertOAuthErrorCode(() -> svc.loadUser(userRequest), "server_error");
    }

    private static void assertOAuthErrorCode(org.junit.jupiter.api.function.Executable call, String expectedCode) {
        assertThatThrownBy(call::execute)
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> assertThat(((OAuth2AuthenticationException) e).getError().getErrorCode())
                        .isEqualTo(expectedCode));
    }
}
