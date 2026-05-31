package ru.x5.devpulse.domain.model.stats;

import java.util.List;
import java.util.Objects;
import ru.x5.devpulse.domain.common.Period;

/**
 * Почасовая статистика за период — разреженная матрица день-недели × час для heatmap
 * «паттерны работы по часам/дням». Обслуживает {@code GET /api/v2/stats/hourly}.
 *
 * <p>{@link #cells} могут быть разреженными: бэк отдаёт только непустые ячейки
 * (commits &gt; 0), фронт достраивает полную сетку 7×24, отсутствующие ключи = 0.</p>
 */
public record HourlyStats(Period period, List<HourlyBucket> cells) {

    public HourlyStats {
        Objects.requireNonNull(period, "period required");
        cells = cells == null ? List.of() : List.copyOf(cells);
    }
}
