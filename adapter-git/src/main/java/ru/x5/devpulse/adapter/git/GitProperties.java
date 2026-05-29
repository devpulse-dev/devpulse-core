package ru.x5.devpulse.adapter.git;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация git-сбора.
 *
 * @param repositories     список URL репозиториев для анализа
 * @param token            access-token; пробрасывается в git CLI через {@code GIT_ASKPASS}
 *                         (env var, не в URL и не в args) — см. {@link GitCliClient}
 * @param cacheDirectory   директория для локальных копий
 * @param commandTimeout   максимальное время на одну команду git (clone/fetch/log).
 *                         По истечении процесс убивается {@code destroyForcibly()} и бросается
 *                         {@link GitCommandFailedException}. Защита от зависших git-процессов
 *                         (DNS lookup, hung proxy, broken pipe). Default: 30 минут.
 */
@ConfigurationProperties(prefix = "git")
public record GitProperties(
        List<String> repositories,
        String token,
        Path cacheDirectory,
        Duration commandTimeout
) {
    private static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofMinutes(30);

    public GitProperties {
        repositories = repositories == null ? List.of() : List.copyOf(repositories);
        commandTimeout = commandTimeout == null ? DEFAULT_COMMAND_TIMEOUT : commandTimeout;
    }
}
