package ru.x5.devpulse.application.port.in;

import java.util.List;
import java.util.Optional;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.stats.DailyAuthorStats;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Возвращает daily-агрегаты за период с опциональными фильтрами.
 * Обслуживает {@code GET /api/v2/stats/daily}.
 *
 * <p>{@code author} и {@code team} — независимые опциональные фильтры (как у
 * {@link GetHourlyStatsUseCase}): {@code author} для профиля одного разработчика,
 * {@code team} — срез по команде. Фильтрация выполняется в БД (WHERE), чтобы типичный запрос
 * не поднимал в heap все daily-строки периода по всем авторам.</p>
 *
 * @param author пусто — без фильтра по автору; задан — только этот автор
 * @param team   пусто — без фильтра по команде; задан — только участники команды
 */
public interface GetDailyStatsUseCase {
    List<DailyAuthorStats> findByPeriod(Period period, Optional<Email> author, Optional<String> team);
}
