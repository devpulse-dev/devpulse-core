package ru.x5.devpulse.domain.model.performance;

/**
 * Cycle-time по карточкам, закрытым в периоде: от перехода «в работу» до «готово».
 *
 * @param medianDays медиана в днях; {@code null}, если данных нет ({@code count == 0})
 * @param meanDays   среднее в днях; {@code null}, если данных нет
 * @param count      сколько карточек участвовало в расчёте
 */
public record CycleTime(Double medianDays, Double meanDays, int count) {

    public static final CycleTime EMPTY = new CycleTime(null, null, 0);
}
