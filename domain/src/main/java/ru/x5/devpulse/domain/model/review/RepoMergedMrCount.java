package ru.x5.devpulse.domain.model.review;

/**
 * Вмерженные MR в одном репозитории за период (независимый от авторов срез).
 *
 * <p>{@code repo} — путь репозитория ({@code namespace/repo}), выведенный из {@code web_url} MR.</p>
 */
public record RepoMergedMrCount(String repo, int count) {
}
