package ru.x5.devpulse.domain.model.performance;

import java.util.List;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Трудозатраты разработчика за период, разложенные по дням (обслуживает {@code GET /stats/timesheet}).
 *
 * <p>Время — в минутах (как в Kaiten), форматирование в часы — задача клиента. Дни без списаний
 * в {@code days} не попадают; {@link #loggedDays()} = их количество (для «среднего за рабочий день»).</p>
 */
public record Timesheet(Email email, Period period, int totalMinutes, List<TimesheetDay> days) {

    public Timesheet {
        days = days == null ? List.of() : List.copyOf(days);
    }

    /** Сколько дней периода с ненулевым списанием. */
    public int loggedDays() {
        return days.size();
    }
}
