package ru.x5.devpulse.application.port.out;

/**
 * GitLab недоступен при аутентификации (сетевой сбой / 5xx) — невозможно проверить токен.
 * Отличается от {@link InvalidGitTokenException} (токен невалиден, 401): здесь токен проверить
 * не удалось. На REST-слое маппится в 503, с понятным «GitLab», а не общим upstream-сообщением.
 */
public class GitUnavailableException extends RuntimeException {

    public GitUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
