package ru.x5.devpulse.adapter.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

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
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value()))
                        .deleteCookies("SESSION"))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
        return http.build();
    }

    /** Явный репозиторий контекста — {@code AuthController} вручную сохраняет сессию после PAT-логина. */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}
