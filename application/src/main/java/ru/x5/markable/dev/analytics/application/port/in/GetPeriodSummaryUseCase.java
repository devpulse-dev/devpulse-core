package ru.x5.markable.dev.analytics.application.port.in;

import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.model.stats.PeriodSummary;

/**
 * Сводка по всем авторам за период (totals + top-N авторов).
 * Обслуживает {@code GET /api/v2/stats/summary}.
 */
public interface GetPeriodSummaryUseCase {
    PeriodSummary summarize(Period period);
}
