package ru.x5.devpulse.domain.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Закрытый интервал дат {@code [from..to]}.
 *
 * <p>Используется везде где нужен фильтр периодом: query API, daily/weekly stats, профиль пользователя.
 * Обе границы — обязательные. Для интервалов с открытой верхней границей передавайте {@code LocalDate.now()}
 * явно — это делает код вызова явнее.</p>
 */
public record Period(LocalDate from, LocalDate to) {

    public Period {
        Objects.requireNonNull(from, "period.from must not be null");
        Objects.requireNonNull(to, "period.to must not be null");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("period.to (" + to + ") is before period.from (" + from + ")");
        }
    }

    /** Полуночь начала периода. */
    public LocalDateTime fromAtStartOfDay() {
        return from.atStartOfDay();
    }

    /** Конец дня конечной даты — для интервалов TIMESTAMP. */
    public LocalDateTime toAtEndOfDay() {
        return to.atTime(LocalTime.MAX);
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(from) && !date.isAfter(to);
    }
}
