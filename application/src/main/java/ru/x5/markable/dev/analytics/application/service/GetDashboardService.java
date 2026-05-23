package ru.x5.markable.dev.analytics.application.service;

import lombok.RequiredArgsConstructor;
import ru.x5.markable.dev.analytics.application.port.in.GetDashboardUseCase;
import ru.x5.markable.dev.analytics.application.port.out.DailyStatsRepository;
import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.model.stats.Dashboard;
import ru.x5.markable.dev.analytics.domain.service.StatsSummarizer;

/** Дашборд: топ-N активных + N аутсайдеров за период. */
@RequiredArgsConstructor
public final class GetDashboardService implements GetDashboardUseCase {

    private final DailyStatsRepository dailyStatsRepository;

    @Override
    public Dashboard get(Period period, int topN, int outsiderN) {
        return StatsSummarizer.dashboard(
                period,
                dailyStatsRepository.findByPeriod(period),
                topN,
                outsiderN);
    }
}
