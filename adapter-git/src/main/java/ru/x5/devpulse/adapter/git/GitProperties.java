package ru.x5.devpulse.adapter.git;

import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация git-сбора.
 *
 * @param repositories     список URL репозиториев для анализа
 * @param token            access-token; пробрасывается в git CLI через {@code GIT_ASKPASS}
 *                         (env var, не в URL и не в args) — см. {@link GitCliClient}
 * @param cacheDirectory   директория для локальных копий
 */
@ConfigurationProperties(prefix = "git")
public record GitProperties(
        List<String> repositories,
        String token,
        Path cacheDirectory
) {
    public GitProperties {
        repositories = repositories == null ? List.of() : List.copyOf(repositories);
    }
}
