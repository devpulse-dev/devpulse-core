package ru.x5.devpulse.adapter.rest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.x5.devpulse.adapter.rest.api.StatsApi;
import ru.x5.devpulse.adapter.rest.api.model.DailyStats;
import ru.x5.devpulse.adapter.rest.api.model.HourlyStats;
import ru.x5.devpulse.adapter.rest.api.model.PeriodSummary;
import ru.x5.devpulse.adapter.rest.api.model.ReviewStats;
import ru.x5.devpulse.adapter.rest.api.model.WeeklyStats;
import ru.x5.devpulse.adapter.rest.mapper.DailyStatsMapper;
import ru.x5.devpulse.adapter.rest.mapper.HourlyStatsMapper;
import ru.x5.devpulse.adapter.rest.mapper.PeriodSummaryMapper;
import ru.x5.devpulse.adapter.rest.mapper.ReviewStatsMapper;
import ru.x5.devpulse.adapter.rest.mapper.WeeklyStatsMapper;
import ru.x5.devpulse.application.port.in.GetDailyStatsUseCase;
import ru.x5.devpulse.application.port.in.GetHourlyStatsUseCase;
import ru.x5.devpulse.application.port.in.GetPeriodSummaryUseCase;
import ru.x5.devpulse.application.port.in.GetReviewStatsUseCase;
import ru.x5.devpulse.application.port.in.GetWeeklyStatsUseCase;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Query-эндпоинты статистики: дневная, недельная, сводка за период, почасовая.
 * Контракт — {@link StatsApi}, сгенерированный из {@code stats-api.yaml}.
 */
@RestController
@RequiredArgsConstructor
class StatsController implements StatsApi {

    private final GetDailyStatsUseCase getDailyStats;
    private final GetWeeklyStatsUseCase getWeeklyStats;
    private final GetPeriodSummaryUseCase getPeriodSummary;
    private final GetHourlyStatsUseCase getHourlyStats;
    private final GetReviewStatsUseCase getReviewStats;

    private final DailyStatsMapper dailyStatsMapper;
    private final WeeklyStatsMapper weeklyStatsMapper;
    private final PeriodSummaryMapper periodSummaryMapper;
    private final HourlyStatsMapper hourlyStatsMapper;
    private final ReviewStatsMapper reviewStatsMapper;

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

    @Override
    public ResponseEntity<HourlyStats> getHourlyStats(LocalDate from, LocalDate to, String email) {
        // email опционален: пусто/blank → агрегат по всей команде.
        Optional<Email> author = (email == null || email.isBlank())
                ? Optional.empty()
                : Optional.of(new Email(email));
        return ResponseEntity.ok(hourlyStatsMapper.toDto(
                getHourlyStats.get(new Period(from, to), author)));
    }

    @Override
    public ResponseEntity<ReviewStats> getReviewStats(LocalDate from, LocalDate to) {
        return ResponseEntity.ok(reviewStatsMapper.toDto(
                getReviewStats.get(new Period(from, to))));
    }
}
