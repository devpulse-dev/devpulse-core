package ru.x5.markable.dev.analytics.application.service;

import lombok.RequiredArgsConstructor;
import ru.x5.markable.dev.analytics.application.port.in.GetPeriodSummaryUseCase;
import ru.x5.markable.dev.analytics.application.port.out.DailyStatsRepository;
import ru.x5.markable.dev.analytics.application.port.out.UnifiedUserRepository;
import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.model.stats.PeriodSummary;
import ru.x5.markable.dev.analytics.domain.service.StatsSummarizer;

/**
 * Сводка за период: totals + top-N авторов c enriched displayName/avatarUrl.
 *
 * <p>Top по умолчанию ≤ 10 (см. {@link StatsSummarizer#summarize}), batch-fetch для них — тривиален.</p>
 */
@RequiredArgsConstructor
public final class GetPeriodSummaryService implements GetPeriodSummaryUseCase {

    private final DailyStatsRepository dailyStatsRepository;
    private final UnifiedUserRepository unifiedUserRepository;

    @Override
    public PeriodSummary summarize(Period period) {
        PeriodSummary raw = StatsSummarizer.summarize(period, dailyStatsRepository.findByPeriod(period));
        var enriched = new AuthorSummaryEnricher(unifiedUserRepository).enrich(raw.topAuthors());
        return raw.withTopAuthors(enriched);
    }
}
