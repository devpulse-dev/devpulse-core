package ru.x5.devpulse.application.port.in;

import java.util.Optional;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.stats.UserProfile;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Профиль пользователя: персональные данные + summary за период + коммиты + карточки Kaiten.
 * Обслуживает {@code GET /api/v2/users/{email}/profile}.
 *
 * <p>Возвращаемый тип {@link UserProfile} живёт в {@code domain.model.stats} — рядом с
 * другими query-DTO ({@code Dashboard}, {@code WeeklyStats}, {@code PeriodSummary}).
 * Вынесен из этого interface'а чтобы порт оставался чистым (см. ArchUnit-правило
 * {@code portsInAreInterfaces}).</p>
 */
public interface GetUserProfileUseCase {

    Optional<UserProfile> findProfile(Email email, Period period);
}
