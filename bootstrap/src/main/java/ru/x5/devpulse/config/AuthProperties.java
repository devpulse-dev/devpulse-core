package ru.x5.devpulse.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация аутентификации (ADR-13).
 *
 * @param admins список email администраторов (роль ADMIN). Источник истины для ADMIN —
 *               конфиг, а не БД (роль derivable). Email матчится после нормализации к lower-case.
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(List<String> admins) {

    public AuthProperties {
        admins = admins == null ? List.of() : List.copyOf(admins);
    }
}
