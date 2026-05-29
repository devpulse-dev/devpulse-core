package ru.x5.devpulse.application.port.in;

import ru.x5.devpulse.domain.common.PageRequest;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.stats.Dashboard;

/**
 * Главный борд: paginated список всех активных авторов за период,
 * отсортированных по не-мердж коммитам по убыванию.
 * Обслуживает {@code GET /api/v2/dashboard}.
 */
public interface GetDashboardUseCase {
    Dashboard get(Period period, PageRequest page);
}
