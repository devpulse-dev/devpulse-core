package ru.x5.devpulse.domain.model.performance;

import java.time.LocalDate;
import java.util.List;

/**
 * Списание за один день таймшита: дата, суммарные минуты и детализация по карточкам
 * (на что ушло время).
 */
public record TimesheetDay(LocalDate date, int minutes, List<TimesheetEntry> entries) {

    public TimesheetDay {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
