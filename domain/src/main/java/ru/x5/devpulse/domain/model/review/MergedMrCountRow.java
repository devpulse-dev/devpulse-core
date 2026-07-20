package ru.x5.devpulse.domain.model.review;

import ru.x5.devpulse.domain.model.user.Email;

/**
 * Строка агрегата «вмержено MR автором за период» — сырьё из БД (GROUP BY author_email).
 *
 * <p>Обогащение (имя/аватар) — уже в сервисе через {@code unified_user}; здесь только email + счётчик.</p>
 */
public record MergedMrCountRow(Email email, long count) {
}
