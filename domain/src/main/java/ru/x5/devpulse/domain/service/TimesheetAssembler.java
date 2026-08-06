package ru.x5.devpulse.domain.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.kaiten.KaitenTimeLog;
import ru.x5.devpulse.domain.model.performance.TimesheetDay;

/**
 * Чистая логика таймшита: суммирование логов Kaiten по дню.
 *
 * <p>За один день бывает несколько списаний (разные карточки/роли) — схлопываем в сумму.
 * Логи вне периода отбрасываем (Kaiten фильтрует сам, но полагаться на это не будем —
 * границы у API включительны не всегда очевидно). Stateless, без I/O.</p>
 */
public final class TimesheetAssembler {

    private TimesheetAssembler() {}

    /**
     * Логи → дни со списаниями, по возрастанию даты. Дни без списаний не создаются
     * (разреженный ряд: период может быть длинным, а списаний — единицы).
     */
    public static List<TimesheetDay> byDay(Collection<KaitenTimeLog> logs, Period period) {
        Map<LocalDate, Integer> minutesByDate = new TreeMap<>();
        for (KaitenTimeLog log : logs) {
            LocalDate date = log.date();
            if (date == null || !period.contains(date)) {
                continue;
            }
            minutesByDate.merge(date, log.minutes(), Integer::sum);
        }
        List<TimesheetDay> days = new ArrayList<>(minutesByDate.size());
        minutesByDate.forEach((date, minutes) -> days.add(new TimesheetDay(date, minutes)));
        return days;
    }

    /** Суммарные минуты по уже посчитанным дням. */
    public static int totalMinutes(Collection<TimesheetDay> days) {
        int total = 0;
        for (TimesheetDay day : days) {
            total += day.minutes();
        }
        return total;
    }
}
