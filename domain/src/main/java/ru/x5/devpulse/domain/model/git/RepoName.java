package ru.x5.devpulse.domain.model.git;

import java.util.Objects;

/**
 * Короткое имя git-репозитория без расширения {@code .git}.
 *
 * <p>Извлекается из URL вида {@code https://scm.x5.ru/group/xrg-core.git} → {@code xrg-core}.
 * Используется как первичный идентификатор репозитория во всех аналитических таблицах.</p>
 */
public record RepoName(String value) {

    public RepoName {
        Objects.requireNonNull(value, "repo name must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("repo name must not be blank");
        }
        value = trimmed;
    }

    /** Парсит из git-URL: возьмёт последний сегмент пути и снимет {@code .git}. */
    public static RepoName fromUrl(String repoUrl) {
        Objects.requireNonNull(repoUrl, "repo url must not be null");
        int slash = repoUrl.lastIndexOf('/');
        String tail = slash < 0 ? repoUrl : repoUrl.substring(slash + 1);
        if (tail.endsWith(".git")) {
            tail = tail.substring(0, tail.length() - 4);
        }
        return new RepoName(tail);
    }

    @Override
    public String toString() {
        return value;
    }
}
