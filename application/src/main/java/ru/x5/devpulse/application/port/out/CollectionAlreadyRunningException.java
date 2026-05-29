package ru.x5.devpulse.application.port.out;

/**
 * Бросается {@link CollectionLock#acquireOrThrow()}, если другой сбор уже идёт.
 *
 * <p>В REST-слое маппится в {@code HTTP 409 Conflict}.</p>
 */
public class CollectionAlreadyRunningException extends RuntimeException {

    public CollectionAlreadyRunningException() {
        super("Сбор уже запущен — параллельные запуски не допускаются");
    }

    public CollectionAlreadyRunningException(Throwable cause) {
        super("Сбор уже запущен — параллельные запуски не допускаются", cause);
    }
}
