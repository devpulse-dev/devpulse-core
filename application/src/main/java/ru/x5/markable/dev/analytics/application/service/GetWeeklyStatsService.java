package ru.x5.markable.dev.analytics.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import ru.x5.markable.dev.analytics.application.port.in.GetWeeklyStatsUseCase;
import ru.x5.markable.dev.analytics.application.port.out.DailyStatsRepository;
import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.model.stats.WeeklyStats;
import ru.x5.markable.dev.analytics.domain.service.StatsSummarizer;

/** Группирует daily-агрегаты по ISO-неделям. */
@RequiredArgsConstructor
public final class GetWeeklyStatsService implements GetWeeklyStatsUseCase {

    private final DailyStatsRepository dailyStatsRepository;

    @Override
    public List<WeeklyStats> findByPeriod(Period period) {
        return StatsSummarizer.weekly(dailyStatsRepository.findByPeriod(period));
    }
}
