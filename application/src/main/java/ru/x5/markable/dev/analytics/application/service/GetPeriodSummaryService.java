package ru.x5.markable.dev.analytics.application.service;

import lombok.RequiredArgsConstructor;
import ru.x5.markable.dev.analytics.application.port.in.GetPeriodSummaryUseCase;
import ru.x5.markable.dev.analytics.application.port.out.DailyStatsRepository;
import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.model.stats.PeriodSummary;
import ru.x5.markable.dev.analytics.domain.service.StatsSummarizer;

/** Сводка за период по всем авторам (totals + top-N). */
@RequiredArgsConstructor
public final class GetPeriodSummaryService implements GetPeriodSummaryUseCase {

    private final DailyStatsRepository dailyStatsRepository;

    @Override
    public PeriodSummary summarize(Period period) {
        return StatsSummarizer.summarize(period, dailyStatsRepository.findByPeriod(period));
    }
}
