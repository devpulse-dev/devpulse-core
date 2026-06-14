package ru.x5.devpulse.domain.model.cohort;

import java.time.YearMonth;
import java.util.List;

/**
 * Retention-треугольник: по строке на когорту (месяц первой активности).
 */
public record CohortRetention(List<CohortRow> cohorts) {

    /**
     * Одна когорта.
     *
     * @param cohort    месяц первой активности
     * @param size      сколько разработчиков впервые активны в этот месяц
     * @param retention доли 0..1 по смещению k (retention.get(0) == 1.0)
     */
    public record CohortRow(YearMonth cohort, int size, List<Double> retention) {}
}
