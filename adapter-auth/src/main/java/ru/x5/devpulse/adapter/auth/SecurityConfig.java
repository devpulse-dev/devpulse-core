package ru.x5.devpulse.adapter.auth;

import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Security-цепочка аутентификации (ADR-13).
 *
 * <ul>
 *   <li>Публично: {@code POST /auth/login} (вход по PAT), actuator health.</li>
 *   <li>Остальное — требует аутентификации; неаутентифицированный → <b>401</b>
 *       ({@link HttpStatusEntryPoint}, не редирект на форму — это SPA).</li>
 *   <li>Сессия в httpOnly-cookie (персист — Spring Session JDBC, см. bootstrap).</li>
 *   <li>CSRF выключен: защита через cookie {@code SameSite=Lax} (см. application.yml) —
 *       кросс-сайтовый POST/fetch не несёт сессию. Токен-CSRF добавим позже при наличии FE.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            ObjectProvider<GitlabOAuth2UserService> oauth2UserService) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v2/auth/login", "/api/v2/auth/config").permitAll()
                        // OAuth2-вход: эндпоинты Spring Security (старт авторизации + callback),
                        // на корне (не /api/v2). Доступны без сессии.
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // RBAC (ADR-13). Контроллеры adapter-rest под префиксом /api/v2
                        // (WebMvcConfig); auth-эндпоинты — нет (другой пакет).
                        // Операционное управление сбором (запуск/отмена прогона) — только ADMIN:
                        // дорогая глобальная операция, не для любого аутентифицированного. GET
                        // (статус/последний прогон) остаётся доступен всем — нужен UI.
                        .requestMatchers(HttpMethod.POST, "/api/v2/collection/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v2/collection/**").hasRole("ADMIN")
                        .requestMatchers("/api/v2/cohorts/**").hasAnyRole("ADMIN", "TEAMLEAD")
                        // Мутация карточек Kaiten (простановка флага AI-Agent) — только elevated.
                        .requestMatchers(HttpMethod.POST, "/api/v2/stats/defects/ai-agent")
                                .hasAnyRole("ADMIN", "TEAMLEAD")
                        // GET /teams — список команд для ГЛОБАЛЬНОГО фильтра в топбаре —
                        // доступен всем аутентифицированным (фильтр на открытых страницах).
                        // Управление (назначение лида/команды) — только elevated.
                        .requestMatchers(HttpMethod.PUT, "/api/v2/teams/**").hasAnyRole("ADMIN", "TEAMLEAD")
                        .requestMatchers(HttpMethod.POST, "/api/v2/teams/**").hasAnyRole("ADMIN", "TEAMLEAD")
                        .requestMatchers(HttpMethod.DELETE, "/api/v2/teams/**").hasAnyRole("ADMIN", "TEAMLEAD")
                        .requestMatchers("/api/v2/users/*/team").hasAnyRole("ADMIN", "TEAMLEAD")
                        .requestMatchers(HttpMethod.GET, "/api/v2/performance/review")
                                .access(this::perfReviewSelfOrElevated)
                        .anyRequest().authenticated())
                .csrf(AbstractHttpConfigurer::disable)
                // logout обрабатывает AuthController (контракт AuthApi) — дефолтный фильтр не нужен.
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

        // OAuth2-вход подключаем ТОЛЬКО если настроена регистрация (GITLAB_OAUTH_CLIENT_ID).
        // Иначе ClientRegistrationRepository-бина нет → контекст грузится без OAuth (PAT-only),
        // а /auth/config отдаёт oauthEnabled=false (фронт не покажет кнопку).
        if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth
                    .userInfoEndpoint(userInfo -> userInfo.userService(oauth2UserService.getObject()))
                    // На SPA: после входа/ошибки — относительные пути приложения.
                    .defaultSuccessUrl("/", true)
                    .failureUrl("/login?error=oauth"));
        }
        return http.build();
    }

    /**
     * Perf-review: ADMIN/TEAMLEAD — по любому разработчику; MEMBER — только по себе
     * ({@code ?email} == email из principal). См. ADR-13.
     */
    private AuthorizationDecision perfReviewSelfOrElevated(
            Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        if (auth == null || !auth.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }
        boolean elevated = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_TEAMLEAD"));
        if (elevated) {
            return new AuthorizationDecision(true);
        }
        String requested = context.getRequest().getParameter("email");
        String self = (auth.getPrincipal() instanceof DevpulsePrincipal p) ? p.email() : null;
        boolean ownData = requested != null && self != null && requested.equalsIgnoreCase(self);
        return new AuthorizationDecision(ownData);
    }
}
