package ru.x5.devpulse.config;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.x5.devpulse.application.port.in.AuthenticateUseCase;
import ru.x5.devpulse.application.port.out.GitIdentityProvider;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.application.service.AuthService;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Wiring аутентификации (ADR-13). {@code auth.admins} (строки) адаптируются в доменные
 * {@link Email} в composition root — {@link AuthService} остаётся Spring-free и принимает
 * уже нормализованный {@code Set<Email>}.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
class AuthUseCaseConfig {

    @Bean
    AuthenticateUseCase authenticateUseCase(GitIdentityProvider gitIdentityProvider,
                                            UnifiedUserRepository unifiedUserRepository,
                                            AuthProperties authProperties) {
        Set<Email> adminEmails = authProperties.admins().stream()
                .filter(s -> s != null && !s.isBlank())
                .map(Email::new) // нормализует к lower-case (инвариант VO)
                .collect(Collectors.toUnmodifiableSet());
        return new AuthService(gitIdentityProvider, unifiedUserRepository, adminEmails);
    }
}
