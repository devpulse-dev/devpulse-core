package ru.x5.markable.dev.analytics.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import ru.x5.markable.dev.analytics.application.port.in.GetDailyStatsUseCase;
import ru.x5.markable.dev.analytics.application.port.out.DailyStatsRepository;
import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.model.stats.DailyAuthorStats;

/** Возвращает все daily-агрегаты за период. */
@RequiredArgsConstructor
public final class GetDailyStatsService implements GetDailyStatsUseCase {

    private final DailyStatsRepository dailyStatsRepository;

    @Override
    public List<DailyAuthorStats> findByPeriod(Period period) {
        return dailyStatsRepository.findByPeriod(period);
    }
}
