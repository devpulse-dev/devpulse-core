package ru.x5.devpulse.application.port.in;

import java.util.Optional;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.performance.PerformanceReview;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Досье к Performance Review по одному человеку за период.
 * Обслуживает {@code GET /api/v2/performance/review}.
 *
 * <p>Возвращает {@link Optional#empty()}, если пользователя нет в {@code unified_user}.</p>
 *
 * @param compareToPrevious считать дельты к предыдущему равному периоду встык перед {@code from}
 */
public interface GetPerformanceReviewUseCase {

    Optional<PerformanceReview> review(Email email, Period period, boolean compareToPrevious);
}
