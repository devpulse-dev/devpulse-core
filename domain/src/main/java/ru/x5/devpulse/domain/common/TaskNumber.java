package ru.x5.devpulse.domain.common;

import java.util.Objects;

/**
 * Идентификатор задачи, привязанный к коммиту через сообщение.
 *
 * <p>В нашем случае это числовой ID карточки Kaiten (например {@code "12345"}),
 * который извлекается парсером из commit message. Конкретный формат —
 * ответственность {@link ru.x5.devpulse.domain.service.CommitMessageParser}.</p>
 */
public record TaskNumber(String value) {

    public TaskNumber {
        Objects.requireNonNull(value, "task number must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("task number must not be blank");
        }
        value = trimmed;
    }

    /** Пробует интерпретировать как числовой ID карточки Kaiten. */
    public java.util.OptionalLong asKaitenCardId() {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? java.util.OptionalLong.of(parsed) : java.util.OptionalLong.empty();
        } catch (NumberFormatException ignore) {
            return java.util.OptionalLong.empty();
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
