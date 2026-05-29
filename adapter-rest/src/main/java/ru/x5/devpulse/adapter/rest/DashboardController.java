package ru.x5.devpulse.adapter.rest;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.x5.devpulse.adapter.rest.dto.DashboardResponse;
import ru.x5.devpulse.application.port.in.GetDashboardUseCase;
import ru.x5.devpulse.domain.common.PageRequest;
import ru.x5.devpulse.domain.common.Period;

/**
 * Главный борд: paginated список всех активных авторов за период,
 * отсортированных по не-мердж коммитам по убыванию.
 *
 * <p>Если {@code from}/{@code to} не переданы — берётся последний 30-дневный период
 * ({@code today-30..today}). Это удобный дефолт для основной страницы фронта.</p>
 */
@RestController
@RequestMapping("/api/v2/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private static final int DEFAULT_WINDOW_DAYS = 30;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final GetDashboardUseCase getDashboard;

    @GetMapping
    public ResponseEntity<DashboardResponse> get(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        Period period = resolvePeriod(from, to);
        return ResponseEntity.ok(DashboardResponse.from(
                getDashboard.get(period, new PageRequest(page, size))));
    }

    /** Подставляет дефолтные границы периода если фронт их опустил. */
    static Period resolvePeriod(LocalDate from, LocalDate to) {
        LocalDate effectiveTo = to == null ? LocalDate.now() : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.minusDays(DEFAULT_WINDOW_DAYS) : from;
        return new Period(effectiveFrom, effectiveTo);
    }
}
