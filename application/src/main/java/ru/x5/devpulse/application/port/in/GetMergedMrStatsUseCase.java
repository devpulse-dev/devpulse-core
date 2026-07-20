package ru.x5.devpulse.application.port.in;

import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.review.TeamMergedMrStats;

/**
 * Статистика вмерженных MR по команде за период. Обслуживает {@code GET /api/v2/stats/merged-mrs}.
 *
 * <p>Данные из БД (собранные ревью-метрики): {@code merged_at} внутри периода, автор — участник команды.</p>
 */
public interface GetMergedMrStatsUseCase {

    TeamMergedMrStats get(String team, Period period);
}
