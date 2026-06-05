package ru.x5.devpulse.application.port.in;

import java.util.List;
import ru.x5.devpulse.domain.model.user.Team;

/**
 * Список команд (имя + лид + участники), деривится из {@code unified_user.team}.
 * Обслуживает {@code GET /api/v2/teams}.
 */
public interface ListTeamsUseCase {

    List<Team> list();
}
