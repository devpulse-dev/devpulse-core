package ru.x5.devpulse.application.port.in;

import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.performance.Timesheet;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Трудозатраты разработчика по дням за период. Обслуживает {@code GET /api/v2/stats/timesheet}.
 *
 * <p>Данные — live из Kaiten ({@code /time-logs}), в БД не хранятся. Пользователь без привязки
 * к Kaiten (или без списаний) даёт пустой таймшит, а не ошибку.</p>
 */
public interface GetTimesheetUseCase {

    Timesheet get(Email email, Period period);
}
