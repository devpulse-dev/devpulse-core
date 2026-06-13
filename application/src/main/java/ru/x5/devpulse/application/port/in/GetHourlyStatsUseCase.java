package ru.x5.devpulse.application.port.in;

import java.util.Optional;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.stats.HourlyStats;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Возвращает почасовую матрицу активности за период.
 * Обслуживает {@code GET /api/v2/stats/hourly}.
 *
 * <p>{@code author} и {@code team} — независимые опциональные фильтры: {@code author} для
 * профиля (один разработчик), {@code team} для страницы «Активность» (срез по команде).</p>
 *
 * @param author пусто — без фильтра по автору; задан — по одному автору (для профиля)
 * @param team   пусто — без фильтра по команде; задан — только участники команды
 */
public interface GetHourlyStatsUseCase {
    HourlyStats get(Period period, Optional<Email> author, Optional<String> team);
}
