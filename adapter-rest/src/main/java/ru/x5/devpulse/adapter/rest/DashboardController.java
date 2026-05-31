package ru.x5.devpulse.adapter.rest;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.x5.devpulse.adapter.rest.api.DashboardApi;
import ru.x5.devpulse.adapter.rest.api.model.DashboardResponse;
import ru.x5.devpulse.adapter.rest.mapper.DashboardMapper;
import ru.x5.devpulse.application.port.in.GetDashboardUseCase;
import ru.x5.devpulse.domain.common.PageRequest;
import ru.x5.devpulse.domain.common.Period;

/**
 * Главный борд: paginated список активных авторов за период, отсортированных
 * по {@code activity.score} убыванию. Контракт — {@link DashboardApi},
 * сгенерированный из {@code dashboard-api.yaml}.
 */
@RestController
@RequiredArgsConstructor
class DashboardController implements DashboardApi {

    private static final int DEFAULT_WINDOW_DAYS = 30;

    private final GetDashboardUseCase getDashboard;
    private final DashboardMapper dashboardMapper;

    @Override
    public ResponseEntity<DashboardResponse> getDashboard(LocalDate from, LocalDate to,
                                                          Integer page, Integer size) {
        Period period = resolvePeriod(from, to);
        return ResponseEntity.ok(dashboardMapper.toDto(
                getDashboard.get(period, new PageRequest(page, size))));
    }

    /** Подставляет дефолтные границы периода если фронт их опустил. */
    static Period resolvePeriod(LocalDate from, LocalDate to) {
        LocalDate effectiveTo = to == null ? LocalDate.now() : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.minusDays(DEFAULT_WINDOW_DAYS) : from;
        return new Period(effectiveFrom, effectiveTo);
    }
}
