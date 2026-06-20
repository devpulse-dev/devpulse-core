package ru.x5.devpulse.adapter.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.x5.devpulse.adapter.auth.dto.AuthConfigResponse;
import ru.x5.devpulse.adapter.auth.dto.AuthMeResponse;
import ru.x5.devpulse.adapter.auth.dto.LoginRequest;
import ru.x5.devpulse.application.port.in.AuthenticateUseCase;
import ru.x5.devpulse.application.port.out.GitUnavailableException;
import ru.x5.devpulse.application.port.out.InvalidGitTokenException;
import ru.x5.devpulse.application.port.out.ProjectAccessDeniedException;
import ru.x5.devpulse.domain.model.user.AuthenticatedUser;
import ru.x5.devpulse.domain.model.user.GitTokenType;

/**
 * Эндпоинты аутентификации (ADR-13). PAT-вход: валидирует токен, провижинит, поднимает
 * SecurityContext и сохраняет его в сессию. {@code /auth/logout} обрабатывает security-цепочка
 * ({@link SecurityConfig}). OAuth2-вход — отдельным чанком.
 */
@RestController
@RequestMapping("/api/v2/auth")
@RequiredArgsConstructor
class AuthController {

    private final AuthenticateUseCase authenticate;
    private final SecurityContextRepository securityContextRepository;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    @GetMapping("/config")
    AuthConfigResponse config() {
        // OAuth доступен, если настроена регистрация клиента (см. SecurityConfig).
        return new AuthConfigResponse(clientRegistrations.getIfAvailable() != null);
    }

    @PostMapping("/login")
    AuthMeResponse login(@RequestBody LoginRequest body,
                         HttpServletRequest request, HttpServletResponse response) {
        if (body == null || body.token() == null || body.token().isBlank()) {
            throw new IllegalArgumentException("token обязателен");
        }
        AuthenticatedUser user = authenticate.authenticate(body.token().trim(), GitTokenType.PAT);
        establishSession(user, request, response);
        return AuthMeResponse.from(user);
    }

    @GetMapping("/me")
    AuthMeResponse me(@AuthenticationPrincipal DevpulsePrincipal principal) {
        return new AuthMeResponse(principal.email(), principal.role().name(),
                principal.name(), principal.avatarUrl(), principal.team());
    }

    /** Поднимаем SecurityContext по результату логина и сохраняем в сессию (Spring Session JDBC). */
    private void establishSession(AuthenticatedUser user,
                                  HttpServletRequest request, HttpServletResponse response) {
        var principal = new DevpulsePrincipal(
                user.email().value(), user.role(), user.name(), user.avatarUrl(), user.team());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
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
