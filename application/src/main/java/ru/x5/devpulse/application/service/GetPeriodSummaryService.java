package ru.x5.devpulse.application.service;

import lombok.RequiredArgsConstructor;
import ru.x5.devpulse.application.port.in.GetPeriodSummaryUseCase;
import ru.x5.devpulse.application.port.out.DailyStatsRepository;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.stats.PeriodSummary;
import ru.x5.devpulse.domain.service.StatsSummarizer;

/**
 * Сводка за период: totals + top-N авторов c enriched displayName/avatarUrl.
 *
 * <p>Top по умолчанию ≤ 10 (см. {@link StatsSummarizer#summarizeAuthors}), batch-fetch для них — тривиален.</p>
 */
@RequiredArgsConstructor
public final class GetPeriodSummaryService implements GetPeriodSummaryUseCase {

    private final DailyStatsRepository dailyStatsRepository;
    private final UnifiedUserRepository unifiedUserRepository;

    @Override
    public PeriodSummary summarize(Period period) {
        // Агрегация по автору — в БД (GROUP BY): totals и top-N считаются на компактном
        // per-author списке, а не на всех daily-строках периода.
        PeriodSummary raw = StatsSummarizer.summarizeAuthors(
                period, dailyStatsRepository.aggregateAuthorsByPeriod(period));
        var enriched = new AuthorSummaryEnricher(unifiedUserRepository).enrich(raw.topAuthors());
        return raw.withTopAuthors(enriched);
    }
}
