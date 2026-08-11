package ru.x5.devpulse.domain.model.review;

/**
 * MR автора в «лёгком» виде — для связки с карточкой Kaiten в таймшите.
 *
 * <p>Номер задачи извлекается из {@code title} тем же парсером, что и у коммитов
 * ({@code 1700-2712833 fix ...} → карточка {@code 2712833}).</p>
 *
 * @param repo путь репозитория ({@code namespace/repo}), выведенный из web_url
 */
public record AuthoredMergeRequest(String repo, String title, String url) {
}
