package ru.x5.devpulse.adapter.gitlab;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Резолв путей GitLab-проектов ({@code namespace/repo}) для обращения к API. Один источник
 * истины, разделяемый между {@code adapter-reviews} (сбор ревью) и {@code adapter-identity}
 * (проверка доступа к проектам). Без состояния — статическая утилита.
 */
@Slf4j
public final class GitlabProjectPaths {

    private GitlabProjectPaths() {
    }

    /**
     * Эффективный список проектов: {@code gitlab.api.projects}, иначе дериват из
     * {@code git.repositories} (clone-URL → {@code namespace/repo}).
     */
    public static List<String> resolve(GitlabProperties properties, GitRepoProperties gitRepos) {
        if (!properties.projects().isEmpty()) {
            return properties.projects();
        }
        List<String> derived = new ArrayList<>();
        for (String url : gitRepos.repositories()) {
            String path = toProjectPath(url);
            if (path != null) {
                derived.add(path);
            }
        }
        return derived;
    }

    /** {@code https://scm.x5.ru/gkr/xrg-core.git} → {@code gkr/xrg-core}. */
    public static String toProjectPath(String cloneUrl) {
        if (cloneUrl == null || cloneUrl.isBlank()) {
            return null;
        }
        try {
            String path = URI.create(cloneUrl.trim()).getPath(); // /gkr/xrg-core.git
            if (path == null) {
                return null;
            }
            path = path.replaceFirst("^/", "").replaceFirst("\\.git$", "");
            return path.isBlank() ? null : path;
        } catch (Exception e) {
            log.warn("GitLab: не распарсил repo URL '{}': {}", cloneUrl, e.getMessage());
            return null;
        }
    }
}
