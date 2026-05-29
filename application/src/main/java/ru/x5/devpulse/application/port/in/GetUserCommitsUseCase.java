package ru.x5.devpulse.application.port.in;

import java.util.List;
import ru.x5.devpulse.domain.common.PageRequest;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Список коммитов пользователя за период с пагинацией.
 * Обслуживает {@code GET /api/v2/users/{email}/commits}.
 */
public interface GetUserCommitsUseCase {
    List<Commit> find(Email email, Period period, PageRequest page);
}
