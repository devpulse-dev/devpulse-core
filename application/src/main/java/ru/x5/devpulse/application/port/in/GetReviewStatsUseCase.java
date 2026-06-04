package ru.x5.devpulse.application.port.in;

import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.review.ReviewStats;

/**
 * Возвращает ревью-метрики по авторам за период.
 * Обслуживает {@code GET /api/v2/stats/reviews}.
 */
public interface GetReviewStatsUseCase {
    ReviewStats get(Period period);
}
