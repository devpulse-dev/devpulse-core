package ru.x5.devpulse.domain.model.performance;

import java.util.Objects;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

/**
 * Read-side агрегат «досье к Performance Review» по одному человеку за период.
 * Обслуживает {@code GET /api/v2/performance/review}.
 *
 * <p>Композиция источников: профиль ({@link UnifiedUser}), git+ревью-метрики с дельтами
 * ({@link PerformanceMetrics}), карточки Kaiten — простой счётчик ({@link TaskTypeBreakdown})
 * и развёрнутая аналитика ({@link KaitenInsights}: дефекты по срочности, rollup разработки,
 * cycle-time, баланс), {@link NotableResults заметные результаты}. Снапшот «как сейчас», не хранится в БД.</p>
 *
 * @param comparedTo предыдущий период сравнения; {@code null}, если сравнение не запрашивалось
 */
public record PerformanceReview(
        UnifiedUser subject,
        Period period,
        Period comparedTo,
        PerformanceMetrics metrics,
        TaskTypeBreakdown taskBreakdown,
        KaitenInsights kaiten,
        NotableResults notable
) {

    public PerformanceReview {
        Objects.requireNonNull(subject, "subject required");
        Objects.requireNonNull(period, "period required");
    }
}
