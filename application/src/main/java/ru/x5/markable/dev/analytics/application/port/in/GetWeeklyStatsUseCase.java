package ru.x5.markable.dev.analytics.application.port.in;

import java.util.List;
import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.model.stats.WeeklyStats;

/**
 * Возвращает недельную статистику за период.
 * Обслуживает {@code GET /api/v2/stats/weekly}.
 */
public interface GetWeeklyStatsUseCase {
    List<WeeklyStats> findByPeriod(Period period);
}
