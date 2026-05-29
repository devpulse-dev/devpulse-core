package ru.x5.devpulse.application.port.in;

import java.util.List;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.stats.DailyAuthorStats;

/**
 * Возвращает все daily-агрегаты за период.
 * Обслуживает {@code GET /api/v2/stats/daily}.
 */
public interface GetDailyStatsUseCase {
    List<DailyAuthorStats> findByPeriod(Period period);
}
