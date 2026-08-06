package ru.x5.devpulse.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.kaiten.KaitenTimeLog;
import ru.x5.devpulse.domain.model.performance.TimesheetDay;

@DisplayName("TimesheetAssembler (списания Kaiten → дни таймшита)")
class TimesheetAssemblerTest {

    private static final Period MAY = new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

    @Test
    @DisplayName("Несколько логов за один день схлопываются в сумму, дни по возрастанию даты")
    void sumsPerDaySorted() {
        List<KaitenTimeLog> logs = List.of(
                new KaitenTimeLog(LocalDate.of(2026, 5, 5), 120),
                new KaitenTimeLog(LocalDate.of(2026, 5, 4), 480),
                new KaitenTimeLog(LocalDate.of(2026, 5, 5), 60),
                new KaitenTimeLog(LocalDate.of(2026, 5, 5), 30));

        List<TimesheetDay> days = TimesheetAssembler.byDay(logs, MAY);

        assertAll(
                () -> assertThat(days).extracting(TimesheetDay::date)
                        .containsExactly(LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 5)),
                () -> assertThat(days.get(0).minutes()).isEqualTo(480),
                () -> assertThat(days.get(1).minutes()).as("120+60+30").isEqualTo(210),
                () -> assertThat(TimesheetAssembler.totalMinutes(days)).isEqualTo(690));
    }

    @Test
    @DisplayName("Логи вне периода и без даты отбрасываются; границы включительны")
    void filtersOutOfPeriodAndNullDate() {
        List<KaitenTimeLog> logs = List.of(
                new KaitenTimeLog(LocalDate.of(2026, 5, 1), 60),    // граница from
                new KaitenTimeLog(LocalDate.of(2026, 5, 31), 60),   // граница to
                new KaitenTimeLog(LocalDate.of(2026, 4, 30), 999),  // до периода
                new KaitenTimeLog(LocalDate.of(2026, 6, 1), 999),   // после периода
                new KaitenTimeLog(null, 999));                      // без даты

        List<TimesheetDay> days = TimesheetAssembler.byDay(logs, MAY);

        assertAll(
                () -> assertThat(days).hasSize(2),
                () -> assertThat(TimesheetAssembler.totalMinutes(days)).isEqualTo(120));
    }

    @Test
    @DisplayName("Пустой вход → пустой список дней и нулевой итог (дни без списаний не создаются)")
    void emptyInput() {
        List<TimesheetDay> days = TimesheetAssembler.byDay(List.of(), MAY);

        assertThat(days).isEmpty();
        assertThat(TimesheetAssembler.totalMinutes(days)).isZero();
    }
}
