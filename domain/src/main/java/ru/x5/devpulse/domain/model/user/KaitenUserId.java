package ru.x5.devpulse.domain.model.user;

/**
 * Идентификатор пользователя в Kaiten.
 *
 * <p>Type-safety обёртка над {@code long}: исключает случайную подстановку
 * {@link ru.x5.devpulse.domain.model.kaiten.KaitenCardId} или
 * gitlab id в API, ожидающее именно Kaiten user id.</p>
 */
public record KaitenUserId(long value) {

    public KaitenUserId {
        if (value <= 0) {
            throw new IllegalArgumentException("kaiten user id must be positive, got: " + value);
        }
    }
}
