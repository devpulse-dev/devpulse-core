package ru.x5.devpulse.domain.model.performance;

import java.time.LocalDate;

/** Списание за один день таймшита: дата + суммарные минуты. */
public record TimesheetDay(LocalDate date, int minutes) {
}
