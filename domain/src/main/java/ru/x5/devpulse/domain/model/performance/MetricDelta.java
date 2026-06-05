package ru.x5.devpulse.domain.model.performance;

/**
 * Значение метрики с дельтой к предыдущему периоду.
 *
 * <p>{@code previous}/{@code delta}/{@code deltaPct} = {@code null}, если сравнение не
 * запрашивалось ИЛИ метрика неисторична (снапшот «как сейчас», например карточки в работе).</p>
 */
public record MetricDelta(double current, Double previous, Double delta, Double deltaPct) {

    /** Снимок без сравнения (неисторичная метрика или сравнение не запрошено). */
    public static MetricDelta snapshot(double current) {
        return new MetricDelta(current, null, null, null);
    }

    /**
     * Значение с дельтой к предыдущему. {@code previous == null} → {@link #snapshot}.
     * {@code deltaPct = (current − previous) / previous × 100}; {@code null} при {@code previous == 0}.
     */
    public static MetricDelta of(double current, Double previous) {
        if (previous == null) {
            return snapshot(current);
        }
        double delta = current - previous;
        Double pct = previous == 0.0 ? null : (delta / previous) * 100.0;
        return new MetricDelta(current, previous, delta, pct);
    }
}
