package ru.x5.markable.dev.analytics.domain.model.stats;

import java.util.List;
import java.util.Objects;
import ru.x5.markable.dev.analytics.domain.common.Period;

/**
 * Главный дашборд: топ-N активных + N аутсайдеров (наименее активные из активных)
 * за {@link #period период}.
 *
 * <p>«Аутсайдеры» — пользователи с минимальным числом коммитов среди тех, у кого
 * за период был хотя бы один коммит. Пустые / отсутствующие в БД авторы сюда не попадают.</p>
 */
public record Dashboard(
        Period period,
        List<AuthorSummary> topActive,
        List<AuthorSummary> outsiders
) {

    public Dashboard {
        Objects.requireNonNull(period, "period required");
        topActive = topActive == null ? List.of() : List.copyOf(topActive);
        outsiders = outsiders == null ? List.of() : List.copyOf(outsiders);
    }
}
