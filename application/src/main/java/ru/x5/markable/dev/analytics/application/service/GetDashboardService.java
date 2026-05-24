package ru.x5.markable.dev.analytics.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import ru.x5.markable.dev.analytics.application.port.in.GetDashboardUseCase;
import ru.x5.markable.dev.analytics.application.port.out.DailyStatsRepository;
import ru.x5.markable.dev.analytics.application.port.out.UnifiedUserRepository;
import ru.x5.markable.dev.analytics.domain.common.Page;
import ru.x5.markable.dev.analytics.domain.common.PageRequest;
import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.model.stats.AuthorSummary;
import ru.x5.markable.dev.analytics.domain.model.stats.Dashboard;
import ru.x5.markable.dev.analytics.domain.service.StatsSummarizer;

/**
 * Дашборд: все активные авторы за период paginated.
 *
 * <p><b>Pipeline:</b></p>
 * <ol>
 *   <li>Тянем daily-stats за период (один SELECT).</li>
 *   <li>Aggregate → отсортированный по не-мердж коммитам список {@link AuthorSummary}.</li>
 *   <li>Применяем page/size → отрезаем только нужную страницу.</li>
 *   <li>Enrichment ({@code displayName}/{@code avatarUrl}) ТОЛЬКО для текущей страницы —
 *       не тратим ресурсы на batch-fetch профилей для всех 200 авторов,
 *       если фронт смотрит только 20.</li>
 * </ol>
 */
@RequiredArgsConstructor
public final class GetDashboardService implements GetDashboardUseCase {

    private final DailyStatsRepository dailyStatsRepository;
    private final UnifiedUserRepository unifiedUserRepository;

    @Override
    public Dashboard get(Period period, PageRequest page) {
        List<AuthorSummary> sorted = StatsSummarizer.activeAuthorsByActivity(
                dailyStatsRepository.findByPeriod(period));

        if (sorted.isEmpty()) {
            return new Dashboard(period, new Page<>(List.of(), page.page(), page.size(), 0));
        }

        Page<AuthorSummary> raw = Page.of(sorted, page);
        List<AuthorSummary> enriched = new AuthorSummaryEnricher(unifiedUserRepository)
                .enrich(raw.items());

        return new Dashboard(period,
                new Page<>(enriched, page.page(), page.size(), raw.totalElements()));
    }
}
