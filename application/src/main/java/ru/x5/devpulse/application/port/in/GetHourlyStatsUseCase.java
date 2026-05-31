package ru.x5.devpulse.application.port.in;

import java.util.Optional;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.stats.HourlyStats;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Возвращает почасовую матрицу активности за период.
 * Обслуживает {@code GET /api/v2/stats/hourly}.
 *
 * @param author пусто — агрегат по всей команде; задан — по одному автору (для профиля)
 */
public interface GetHourlyStatsUseCase {
    HourlyStats get(Period period, Optional<Email> author);
}
