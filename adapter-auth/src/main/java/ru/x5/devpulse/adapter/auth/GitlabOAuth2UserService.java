package ru.x5.devpulse.adapter.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import ru.x5.devpulse.application.port.in.AuthenticateUseCase;
import ru.x5.devpulse.application.port.out.GitUnavailableException;
import ru.x5.devpulse.application.port.out.InvalidGitTokenException;
import ru.x5.devpulse.application.port.out.ProjectAccessDeniedException;
import ru.x5.devpulse.domain.model.user.AuthenticatedUser;
import ru.x5.devpulse.domain.model.user.GitTokenType;

/**
 * Грузит пользователя при OAuth2-входе через наш {@link AuthenticateUseCase} (GET /user
 * OAuth-токеном + проверка доступа + провижининг + роль) — вместо дефолтного userinfo-сервиса.
 * Возвращает {@link DevpulsePrincipal} (он же {@code OAuth2User}) — единый principal с PAT-входом.
 * Отказы транслируются в {@link OAuth2AuthenticationException} → failureUrl.
 */
@Component
@RequiredArgsConstructor
class GitlabOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final AuthenticateUseCase authenticate;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        String token = userRequest.getAccessToken().getTokenValue();
        try {
            AuthenticatedUser user = authenticate.authenticate(token, GitTokenType.OAUTH);
            return new DevpulsePrincipal(
                    user.email().value(), user.role(), user.name(), user.avatarUrl(), user.team());
        } catch (ProjectAccessDeniedException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error("access_denied"), e);
        } catch (InvalidGitTokenException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), e);
        } catch (GitUnavailableException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error("server_error"), e);
        }
    }
}
