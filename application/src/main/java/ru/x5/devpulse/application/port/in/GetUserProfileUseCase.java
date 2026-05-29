package ru.x5.devpulse.application.port.in;

import java.util.List;
import java.util.Optional;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.kaiten.KaitenCard;
import ru.x5.devpulse.domain.model.stats.AuthorSummary;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

/**
 * Профиль пользователя: персональные данные + summary за период + коммиты + карточки Kaiten.
 * Обслуживает {@code GET /api/v2/users/{email}/profile}.
 */
public interface GetUserProfileUseCase {

    Optional<Profile> findProfile(Email email, Period period);

    record Profile(
            UnifiedUser user,
            AuthorSummary summary,
            List<Commit> commits,
            List<KaitenCard> cards
    ) {}
}
