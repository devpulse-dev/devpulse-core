package ru.x5.devpulse.adapter.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ru.x5.devpulse.adapter.auth.api.AuthApi;
import ru.x5.devpulse.adapter.auth.api.model.AuthConfigResponse;
import ru.x5.devpulse.adapter.auth.api.model.AuthMeResponse;
import ru.x5.devpulse.adapter.auth.api.model.LoginRequest;
import ru.x5.devpulse.adapter.auth.api.model.Role;
import ru.x5.devpulse.application.port.in.AuthenticateUseCase;
import ru.x5.devpulse.application.port.out.GitUnavailableException;
import ru.x5.devpulse.application.port.out.InvalidGitTokenException;
import ru.x5.devpulse.application.port.out.ProjectAccessDeniedException;
import ru.x5.devpulse.domain.model.user.AuthenticatedUser;
import ru.x5.devpulse.domain.model.user.GitTokenType;

/**
 * Аутентификация (ADR-13). Реализует сгенерированный из {@code auth-api.yaml} {@link AuthApi}
 * (контракт — источник истины). Пути {@code /auth/*} получают префикс {@code /api/v2} от
 * {@code WebMvcConfig} (предикат расширен на пакет {@code adapter.auth}).
 *
 * <p>Сессию поднимаем сами: интерфейс {@code AuthApi} не даёт {@code HttpServletResponse},
 * поэтому пишем {@code SecurityContext} прямо в HTTP-сессию тем же ключом, что читает
 * {@link HttpSessionSecurityContextRepository} на последующих запросах. OAuth2-вход — через
 * {@code GitlabOAuth2UserService} (см. SecurityConfig).</p>
 */
@RestController
@RequiredArgsConstructor
class AuthController implements AuthApi {

    private final AuthenticateUseCase authenticate;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    @Override
    public ResponseEntity<AuthMeResponse> loginWithToken(LoginRequest loginRequest) {
        if (loginRequest == null || loginRequest.getToken() == null || loginRequest.getToken().isBlank()) {
            throw new IllegalArgumentException("token обязателен");
        }
        AuthenticatedUser user = authenticate.authenticate(loginRequest.getToken().trim(), GitTokenType.PAT);
        establishSession(user);
        return ResponseEntity.ok(toDto(user));
    }

    @Override
    public ResponseEntity<AuthMeResponse> getAuthMe() {
        DevpulsePrincipal principal = currentPrincipal();
        return ResponseEntity.ok(new AuthMeResponse()
                .email(principal.email())
                .role(Role.fromValue(principal.role().name()))
                .name(principal.name())
                .avatarUrl(principal.avatarUrl())
                .team(principal.team()));
    }

    @Override
    public ResponseEntity<Void> logout() {
        HttpSession session = currentRequest().getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<AuthConfigResponse> getAuthConfig() {
        // OAuth доступен, если настроена регистрация клиента (см. SecurityConfig).
        return ResponseEntity.ok(
                new AuthConfigResponse().oauthEnabled(clientRegistrations.getIfAvailable() != null));
    }

    private static AuthMeResponse toDto(AuthenticatedUser user) {
        return new AuthMeResponse()
                .email(user.email().value())
                .role(Role.fromValue(user.role().name()))
                .name(user.name())
                .avatarUrl(user.avatarUrl())
                .team(user.team());
    }

    private void establishSession(AuthenticatedUser user) {
        var principal = new DevpulsePrincipal(
                user.email().value(), user.role(), user.name(), user.avatarUrl(), user.team());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        currentRequest().getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    private static DevpulsePrincipal currentPrincipal() {
        return (DevpulsePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private static HttpServletRequest currentRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }

    @ExceptionHandler(InvalidGitTokenException.class)
    ProblemDetail handleInvalidToken(InvalidGitTokenException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(ProjectAccessDeniedException.class)
    ProblemDetail handleAccessDenied(ProjectAccessDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(GitUnavailableException.class)
    ProblemDetail handleGitUnavailable(GitUnavailableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }
}
