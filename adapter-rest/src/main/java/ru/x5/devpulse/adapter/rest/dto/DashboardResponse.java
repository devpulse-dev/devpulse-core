package ru.x5.devpulse.adapter.rest.dto;

import java.time.LocalDate;
import java.util.List;
import ru.x5.devpulse.domain.model.stats.Dashboard;

/**
 * Главный борд: paginated список всех активных авторов за период.
 * Сортировка — по убыванию не-мердж коммитов.
 *
 * <p>Фронт обычно показывает первую страницу как «топ» и последнюю как «аутсайдеры»;
 * раздельной секции для outsiders в этом API нет.</p>
 */
public record DashboardResponse(
        LocalDate from,
        LocalDate to,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        List<AuthorSummaryResponse> items
) {
    public static DashboardResponse from(Dashboard d) {
        var p = d.authors();
        return new DashboardResponse(
                d.period().from(),
                d.period().to(),
                p.page(),
                p.size(),
                p.totalElements(),
                p.totalPages(),
                p.hasNext(),
                p.items().stream().map(AuthorSummaryResponse::from).toList());
    }
}
