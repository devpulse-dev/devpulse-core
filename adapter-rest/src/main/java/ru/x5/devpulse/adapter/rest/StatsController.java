package ru.x5.devpulse.adapter.rest;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.x5.devpulse.adapter.rest.dto.DailyStatsResponse;
import ru.x5.devpulse.adapter.rest.dto.PeriodSummaryResponse;
import ru.x5.devpulse.adapter.rest.dto.WeeklyStatsResponse;
import ru.x5.devpulse.application.port.in.GetDailyStatsUseCase;
import ru.x5.devpulse.application.port.in.GetPeriodSummaryUseCase;
import ru.x5.devpulse.application.port.in.GetWeeklyStatsUseCase;
import ru.x5.devpulse.domain.common.Period;

/**
 * Query-эндпоинты статистики: дневная, недельная, сводка за период.
 * Период обязателен у всех — {@code from} и {@code to} в ISO-формате.
 */
@RestController
@RequestMapping("/api/v2/stats")
@RequiredArgsConstructor
public class StatsController {

    private final GetDailyStatsUseCase getDailyStats;
    private final GetWeeklyStatsUseCase getWeeklyStats;
    private final GetPeriodSummaryUseCase getPeriodSummary;

    @GetMapping("/daily")
    public ResponseEntity<List<DailyStatsResponse>> daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var data = getDailyStats.findByPeriod(new Period(from, to)).stream()
                .map(DailyStatsResponse::from).toList();
        return ResponseEntity.ok(data);
    }

    @GetMapping("/weekly")
    public ResponseEntity<List<WeeklyStatsResponse>> weekly(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var data = getWeeklyStats.findByPeriod(new Period(from, to)).stream()
                .map(WeeklyStatsResponse::from).toList();
        return ResponseEntity.ok(data);
    }

    @GetMapping("/summary")
    public ResponseEntity<PeriodSummaryResponse> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(PeriodSummaryResponse.from(
                getPeriodSummary.summarize(new Period(from, to))));
    }
}
