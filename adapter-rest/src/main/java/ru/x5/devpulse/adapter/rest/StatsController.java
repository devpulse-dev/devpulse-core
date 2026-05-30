package ru.x5.devpulse.adapter.rest;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.x5.devpulse.adapter.rest.api.StatsApi;
import ru.x5.devpulse.adapter.rest.api.model.DailyStats;
import ru.x5.devpulse.adapter.rest.api.model.PeriodSummary;
import ru.x5.devpulse.adapter.rest.api.model.WeeklyStats;
import ru.x5.devpulse.adapter.rest.mapper.DailyStatsMapper;
import ru.x5.devpulse.adapter.rest.mapper.PeriodSummaryMapper;
import ru.x5.devpulse.adapter.rest.mapper.WeeklyStatsMapper;
import ru.x5.devpulse.application.port.in.GetDailyStatsUseCase;
import ru.x5.devpulse.application.port.in.GetPeriodSummaryUseCase;
import ru.x5.devpulse.application.port.in.GetWeeklyStatsUseCase;
import ru.x5.devpulse.domain.common.Period;

/**
 * Query-эндпоинты статистики: дневная, недельная, сводка за период.
 * Контракт — {@link StatsApi}, сгенерированный из {@code stats-api.yaml}.
 */
@RestController
@RequiredArgsConstructor
class StatsController implements StatsApi {

    private final GetDailyStatsUseCase getDailyStats;
    private final GetWeeklyStatsUseCase getWeeklyStats;
    private final GetPeriodSummaryUseCase getPeriodSummary;

    private final DailyStatsMapper dailyStatsMapper;
    private final WeeklyStatsMapper weeklyStatsMapper;
    private final PeriodSummaryMapper periodSummaryMapper;

    @Override
    public ResponseEntity<List<DailyStats>> getDailyStats(LocalDate from, LocalDate to) {
        var data = getDailyStats.findByPeriod(new Period(from, to)).stream()
                .map(dailyStatsMapper::toDto).toList();
        return ResponseEntity.ok(data);
    }

    @Override
    public ResponseEntity<List<WeeklyStats>> getWeeklyStats(LocalDate from, LocalDate to) {
        var data = getWeeklyStats.findByPeriod(new Period(from, to)).stream()
                .map(weeklyStatsMapper::toDto).toList();
        return ResponseEntity.ok(data);
    }

    @Override
    public ResponseEntity<PeriodSummary> getSummaryStats(LocalDate from, LocalDate to) {
        return ResponseEntity.ok(periodSummaryMapper.toDto(
                getPeriodSummary.summarize(new Period(from, to))));
    }
}
