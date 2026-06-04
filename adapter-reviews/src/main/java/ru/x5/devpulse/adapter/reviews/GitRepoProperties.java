package ru.x5.devpulse.adapter.reviews;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Читает {@code git.repositories} (тот же список clone-URL, что использует git-сбор),
 * чтобы вывести пути GitLab-проектов для сбора ревью — без дублирования списка в конфиге.
 *
 * <p>Отдельная привязка к префиксу {@code git} (не зависим от {@code adapter-git});
 * Spring допускает несколько {@code @ConfigurationProperties} на один префикс.</p>
 */
@ConfigurationProperties(prefix = "git")
public record GitRepoProperties(List<String> repositories) {
    public GitRepoProperties {
        repositories = repositories == null ? List.of() : List.copyOf(repositories);
    }
}
