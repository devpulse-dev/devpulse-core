package ru.x5.devpulse.application.port.in;

import java.util.List;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.performance.TeamDefectsReport;

/**
 * Уникальные дефекты команды по приоритету за несколько периодов.
 * Обслуживает {@code POST /api/v2/stats/defects}.
 *
 * <p>Дефекты тянутся live из Kaiten по участникам команды и дедуплицируются по id карточки
 * (в одной карточке может быть несколько участников — считаем один раз).</p>
 */
public interface GetTeamDefectsUseCase {

    /**
     * @param team    имя команды (значение {@code unified_user.team})
     * @param periods периоды (1..10) — валидируется на границе REST через контракт
     * @return отчёт с разбивкой по приоритету на каждый период (в том же порядке)
     */
    TeamDefectsReport get(String team, List<Period> periods);
}
