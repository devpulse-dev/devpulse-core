package ru.x5.devpulse.domain.model.performance;

import java.util.List;
import java.util.Objects;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

/**
 * Read-side агрегат «досье к Performance Review» по одному человеку за период.
 * Обслуживает {@code GET /api/v2/performance/review}.
 *
 * <p>Композиция четырёх источников: профиль ({@link UnifiedUser}), git+ревью-метрики с
 * дельтами ({@link PerformanceMetrics}), карточки Kaiten ({@link TaskTypeBreakdown},
 * снапшот «как сейчас») и highlights-пруфы. Не хранится в БД — формируется on-the-fly
 * use case'ом из уже собранных данных.</p>
 *
 * @param comparedTo предыдущий период сравнения; {@code null}, если сравнение не запрашивалось
 */
public record PerformanceReview(
        UnifiedUser subject,
        Period period,
        Period comparedTo,
        PerformanceMetrics metrics,
        TaskTypeBreakdown taskBreakdown,
        List<PerformanceHighlight> highlights
) {

    public PerformanceReview {
        Objects.requireNonNull(subject, "subject required");
        Objects.requireNonNull(period, "period required");
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
    }
}
