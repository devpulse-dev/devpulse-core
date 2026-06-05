package ru.x5.devpulse.application.port.in;

import java.util.Optional;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

/**
 * Назначить/снять команду пользователя (управление командами с фронта).
 * Обслуживает {@code PUT /api/v2/users/{email}/team}.
 *
 * <p>Возвращает {@link Optional#empty()}, если пользователя нет в {@code unified_user}.</p>
 *
 * @param team имя команды; {@code null} — снять привязку
 */
public interface SetUserTeamUseCase {

    Optional<UnifiedUser> setTeam(Email email, String team);
}
