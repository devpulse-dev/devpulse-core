package ru.x5.devpulse.application.port.out;

/**
 * Токен GitLab невалиден/отозван — {@code GET /user} вернул 401. На REST-слое маппится в 401
 * (прецедент размещения интеграционного исключения в {@code port.out}: {@code
 * CollectionAlreadyRunningException}).
 */
public class InvalidGitTokenException extends RuntimeException {

    public InvalidGitTokenException(String message) {
        super(message);
    }

    public InvalidGitTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
