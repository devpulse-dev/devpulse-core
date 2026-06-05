package ru.x5.devpulse.application.port.in;

import java.util.Optional;
import ru.x5.devpulse.domain.model.user.Team;

/**
 * Назначить/снять лида команды (один лид на команду).
 * Обслуживает {@code PUT /api/v2/teams/lead}.
 *
 * <p>{@code email != null} — новый лид (добавляется в команду, прежний лид теряет признак);
 * {@code email == null} — снять лида. {@link Optional#empty()} → 404 (нет пользователя
 * либо при снятии лида команда не существует).</p>
 */
public interface SetTeamLeadUseCase {

    Optional<Team> setLead(String team, String email);
}
