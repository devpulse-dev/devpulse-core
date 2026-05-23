package ru.x5.markable.dev.analytics.application.port.in;

import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.model.stats.Dashboard;

/**
 * Главный борд: топ-N активных + N аутсайдеров за период.
 * Обслуживает {@code GET /api/v2/dashboard}.
 */
public interface GetDashboardUseCase {
    Dashboard get(Period period, int topN, int outsiderN);
}
