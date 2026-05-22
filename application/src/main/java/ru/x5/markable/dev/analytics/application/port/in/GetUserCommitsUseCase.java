package ru.x5.markable.dev.analytics.application.port.in;

import java.util.List;
import ru.x5.markable.dev.analytics.domain.common.PageRequest;
import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.model.git.Commit;
import ru.x5.markable.dev.analytics.domain.model.user.Email;

/**
 * Список коммитов пользователя за период с пагинацией.
 * Обслуживает {@code GET /api/v2/users/{email}/commits}.
 */
public interface GetUserCommitsUseCase {
    List<Commit> find(Email email, Period period, PageRequest page);
}
