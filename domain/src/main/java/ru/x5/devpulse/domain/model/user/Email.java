package ru.x5.devpulse.domain.model.user;

import java.util.Locale;
import java.util.Objects;

/**
 * Адрес электронной почты, нормализованный к нижнему регистру.
 *
 * <p>Идентифицирует автора в Git и пользователя в Kaiten/UnifiedUser.</p>
 *
 * <p><b>Инвариант:</b> после конструктора {@link #value()} <b>всегда</b>:
 * <ul>
 *   <li>trim'нут от ведущих/конечных пробелов;</li>
 *   <li>приведён к нижнему регистру через {@code toLowerCase(Locale.ROOT)} — <b>стабильно
 *       независимо от системной локали</b> (обычный {@code toLowerCase()} под локалью tr-TR
 *       превратил бы {@code "I"} в {@code "ı"} без точки и сломал бы матчинг email);</li>
 *   <li>содержит {@code @} с непустыми local-part и доменом, в домене присутствует {@code .}</li>
 * </ul>
 * Адаптеры МОГУТ полагаться на этот инвариант и НЕ должны делать ещё один {@code toLowerCase()}
 * перед отправкой в БД или внешние системы. Если адаптер тащит email из строки извне (REST query
 * string, git author email), он строит {@code new Email(raw)} и получает нормализованное значение.</p>
 *
 * <p>Валидация — минимально достаточная: непустая строка с {@code @} и точкой после {@code @}.
 * Глубокий RFC-парсинг намеренно не делается — это вне задачи доменной модели.</p>
 */
public record Email(String value) {

    public Email {
        Objects.requireNonNull(value, "email must not be null");
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
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
