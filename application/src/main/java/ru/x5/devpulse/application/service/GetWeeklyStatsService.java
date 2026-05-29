package ru.x5.devpulse.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import ru.x5.devpulse.application.port.in.GetWeeklyStatsUseCase;
import ru.x5.devpulse.application.port.out.DailyStatsRepository;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.stats.AuthorSummary;
import ru.x5.devpulse.domain.model.stats.WeeklyStats;
import ru.x5.devpulse.domain.service.StatsSummarizer;

/**
 * Недельная статистика с enriched authors.
 *
 * <p>Один batch-fetch профилей для всех уникальных email'ов по всему набору недель —
 * без N+1 запросов на каждую неделю.</p>
 */
@RequiredArgsConstructor
public final class GetWeeklyStatsService implements GetWeeklyStatsUseCase {

    private final DailyStatsRepository dailyStatsRepository;
    private final UnifiedUserRepository unifiedUserRepository;

    @Override
    public List<WeeklyStats> findByPeriod(Period period) {
        List<WeeklyStats> weeks = StatsSummarizer.weekly(dailyStatsRepository.findByPeriod(period));
        if (weeks.isEmpty()) return weeks;

        // Один batch на все недели сразу.
        List<List<AuthorSummary>> groups = new ArrayList<>(weeks.size());
        for (WeeklyStats w : weeks) groups.add(w.authors());

        Function<List<AuthorSummary>, List<AuthorSummary>> enricher =
                new AuthorSummaryEnricher(unifiedUserRepository).batchEnricher(groups);

        List<WeeklyStats> result = new ArrayList<>(weeks.size());
        for (WeeklyStats w : weeks) {
            result.add(w.withAuthors(enricher.apply(w.authors())));
        }
        return result;
    }
}
