package ru.x5.devpulse.domain.model.cohort;

import java.util.List;
import ru.x5.devpulse.domain.model.stats.ActivityCategory;

/**
 * Матрица переходов тиров активности месяц-к-месяцу.
 *
 * @param tiers  порядок тиров (строки/колонки матрицы): INACTIVE, BELOW_AVERAGE, ACTIVE, STAR
 * @param matrix {@code matrix.get(i).get(j)} — доля переходов из тира i в тир j; сумма строки = 1
 *               (или 0, если из тира i не было ни одного перехода)
 */
public record TierTransitions(List<ActivityCategory> tiers, List<List<Double>> matrix) {}
