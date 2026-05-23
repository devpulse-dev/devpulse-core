package ru.x5.markable.dev.analytics.adapter.rest.dto;

import java.time.LocalDate;
import java.util.List;
import ru.x5.markable.dev.analytics.domain.model.stats.Dashboard;

/** Главный борд для REST-ответа: top-N активных + N аутсайдеров за период. */
public record DashboardResponse(
        LocalDate from,
        LocalDate to,
        List<AuthorSummaryResponse> topActive,
        List<AuthorSummaryResponse> outsiders
) {
    public static DashboardResponse from(Dashboard d) {
        return new DashboardResponse(
                d.period().from(), d.period().to(),
                d.topActive().stream().map(AuthorSummaryResponse::from).toList(),
                d.outsiders().stream().map(AuthorSummaryResponse::from).toList());
    }
}
