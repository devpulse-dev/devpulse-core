package ru.x5.devpulse.application.port.in;

import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.stats.PeriodSummary;

/**
 * Сводка по всем авторам за период (totals + top-N авторов).
 * Обслуживает {@code GET /api/v2/stats/summary}.
 */
public interface GetPeriodSummaryUseCase {
    PeriodSummary summarize(Period period);
}
