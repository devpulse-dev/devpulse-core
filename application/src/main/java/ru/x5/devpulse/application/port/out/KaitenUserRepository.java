package ru.x5.devpulse.application.port.out;

import java.util.Collection;
import java.util.Optional;
import ru.x5.devpulse.domain.model.kaiten.KaitenUser;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.KaitenUserId;

/**
 * Port out: персистентность пользователей Kaiten.
 */
public interface KaitenUserRepository {

    Optional<KaitenUser> findById(KaitenUserId id);

    Optional<KaitenUser> findByEmail(Email email);

    void upsertAll(Collection<KaitenUser> users);
}
