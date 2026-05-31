package ru.x5.devpulse.domain.model.stats;

/**
 * Одна ячейка почасовой матрицы активности: агрегат не-мердж коммитов в координате
 * {@code (день недели × час)}.
 *
 * @param weekday    день недели по ISO: 0=Пн, 1=Вт … 6=Вс
 * @param hour       час суток 0..23 (по {@code commit_date}, naive local time сбора)
 * @param commits    количество не-мердж коммитов в этой ячейке
 * @param addedLines сумма добавленных строк в этой ячейке
 */
public record HourlyBucket(int weekday, int hour, long commits, long addedLines) {

    public HourlyBucket {
        if (weekday < 0 || weekday > 6) {
            throw new IllegalArgumentException("weekday must be 0..6 (0=Mon..6=Sun), got " + weekday);
        }
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("hour must be 0..23, got " + hour);
        }
        if (commits < 0 || addedLines < 0) {
            throw new IllegalArgumentException(
                    "counters must be non-negative: commits=" + commits + " addedLines=" + addedLines);
        }
    }
}
