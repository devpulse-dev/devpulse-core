package ru.x5.markable.dev.analytics.domain.model.user;

import java.util.Objects;

/**
 * Адрес электронной почты, нормализованный к нижнему регистру.
 *
 * <p>Идентифицирует автора в Git и пользователя в Kaiten/UnifiedUser.
 * Валидация — минимально достаточная: непустая строка с {@code @} и точкой после {@code @}.
 * Глубокий RFC-парсинг намеренно не делается — это вне задачи доменной модели.</p>
 */
public record Email(String value) {

    public Email {
        Objects.requireNonNull(value, "email must not be null");
        String trimmed = value.trim().toLowerCase();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        int at = trimmed.indexOf('@');
        if (at <= 0 || at == trimmed.length() - 1 || trimmed.indexOf('.', at) < 0) {
            throw new IllegalArgumentException("email is not well-formed: " + value);
        }
        value = trimmed;
    }

    /** Часть до {@code @} — для отображения в UI как username по умолчанию. */
    public String localPart() {
        return value.substring(0, value.indexOf('@'));
    }

    @Override
    public String toString() {
        return value;
    }
}
