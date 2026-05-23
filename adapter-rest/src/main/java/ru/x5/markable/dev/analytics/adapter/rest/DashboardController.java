package ru.x5.markable.dev.analytics.adapter.rest;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.x5.markable.dev.analytics.adapter.rest.dto.DashboardResponse;
import ru.x5.markable.dev.analytics.application.port.in.GetDashboardUseCase;
import ru.x5.markable.dev.analytics.domain.common.Period;

/**
 * Главный борд фронта: топ-N активных + N аутсайдеров.
 *
 * <p>Если {@code from}/{@code to} не переданы — берётся последний 30-дневный период
 * ({@code today-30..today}). Это удобный дефолт для основной страницы.</p>
 */
@RestController
@RequestMapping("/api/v2/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private static final int DEFAULT_WINDOW_DAYS = 30;
    private static final int DEFAULT_TOP_N = 10;
    private static final int DEFAULT_OUTSIDER_N = 10;

    private final GetDashboardUseCase getDashboard;

    @GetMapping
    public ResponseEntity<DashboardResponse> get(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "" + DEFAULT_TOP_N) int topN,
            @RequestParam(defaultValue = "" + DEFAULT_OUTSIDER_N) int outsiderN) {
        Period period = resolvePeriod(from, to);
        return ResponseEntity.ok(DashboardResponse.from(getDashboard.get(period, topN, outsiderN)));
    }

    /** Подставляет дефолтные границы периода если фронт их опустил. */
    static Period resolvePeriod(LocalDate from, LocalDate to) {
        LocalDate effectiveTo = to == null ? LocalDate.now() : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.minusDays(DEFAULT_WINDOW_DAYS) : from;
        return new Period(effectiveFrom, effectiveTo);
    }
}
