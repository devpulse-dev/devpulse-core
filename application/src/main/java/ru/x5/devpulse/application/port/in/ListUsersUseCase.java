package ru.x5.devpulse.application.port.in;

import java.util.List;
import java.util.Optional;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

/**
 * Список пользователей (для picker'а perf-review и управления командами).
 * Обслуживает {@code GET /api/v2/users}.
 *
 * @param team фильтр по команде; пусто — все пользователи
 */
public interface ListUsersUseCase {

    List<UnifiedUser> list(Optional<String> team);
}
