package ru.x5.devpulse.domain.model.kaiten;

/**
 * Идентификатор карточки в Kaiten.
 *
 * <p>Совпадает с числовым ID, который Kaiten отдаёт в API.
 * Используется как PK в таблице {@code kaiten_card}.</p>
 */
public record KaitenCardId(long value) {

    public KaitenCardId {
        if (value <= 0) {
            throw new IllegalArgumentException("kaiten card id must be positive, got: " + value);
        }
    }
}
