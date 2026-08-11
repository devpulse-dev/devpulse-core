package ru.x5.devpulse.domain.model.stats;

import java.util.Comparator;
import java.util.List;

/**
 * Одна ячейка почасовой матрицы активности: агрегат не-мердж коммитов в координате
 * {@code (день недели × час)}.
 *
 * <p>{@link #authors} — кто именно коммитил в этот час, по убыванию коммитов.
 * Список может быть пустым (агрегат без разбивки), но не {@code null}; счётчики ячейки
 * при непустом списке равны сумме по авторам.</p>
 *
 * @param weekday    день недели по ISO: 0=Пн, 1=Вт … 6=Вс
 * @param hour       час суток 0..23 (по {@code commit_date}, naive local time сбора)
 * @param commits    количество не-мердж коммитов в этой ячейке
 * @param addedLines сумма добавленных строк в этой ячейке
 * @param authors    вклад отдельных авторов, по убыванию коммитов
 */
public record HourlyBucket(
        int weekday, int hour, long commits, long addedLines, List<HourlyBucketAuthor> authors) {

    /** Ячейка без разбивки по авторам. */
    public HourlyBucket(int weekday, int hour, long commits, long addedLines) {
        this(weekday, hour, commits, addedLines, List.of());
    }

    /**
     * Ячейка из вклада авторов: счётчики складываются, порядок нормализуется к
     * убыванию коммитов — вызывающему не нужно сортировать самому.
     */
    public static HourlyBucket of(int weekday, int hour, List<HourlyBucketAuthor> authors) {
        List<HourlyBucketAuthor> sorted = authors.stream()
                // Явный type witness: без него вывод типа ломается на .reversed()
                // после comparingLong с method reference.
                .sorted(Comparator.<HourlyBucketAuthor>comparingLong(HourlyBucketAuthor::commits).reversed())
                .toList();
        long commits = sorted.stream().mapToLong(HourlyBucketAuthor::commits).sum();
        long addedLines = sorted.stream().mapToLong(HourlyBucketAuthor::addedLines).sum();
        return new HourlyBucket(weekday, hour, commits, addedLines, sorted);
    }

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
        authors = authors == null ? List.of() : List.copyOf(authors);
    }
}
