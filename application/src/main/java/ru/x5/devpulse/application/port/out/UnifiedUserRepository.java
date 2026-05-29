package ru.x5.devpulse.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.KaitenUserId;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

/**
 * Port out: персистентность объединённых пользователей.
 */
public interface UnifiedUserRepository {

    Optional<UnifiedUser> findByEmail(Email email);

    /**
     * Batch-чтение пользователей по списку email — один SELECT с {@code WHERE email IN (?)}.
     *
     * <p>Возвращает только реально существующие записи (без create). Используется
     * для enrichment-сценариев — например, подтянуть {@code avatarUrl}/{@code name}
     * к списку авторов в дашборде.</p>
     */
    List<UnifiedUser> findByEmails(Collection<Email> emails);

    /**
     * Batch find-or-create по списку email.
     *
     * <p>Гарантирует: каждый указанный email будет иметь запись в {@code unified_user}
     * к моменту возврата. Реализация выполняет 1 SELECT + при необходимости 1 batch-INSERT.</p>
     *
     * @return карта email → userId
     */
    Map<Email, Long> findOrCreateAll(Collection<Email> emails);

    List<UnifiedUser> findAll();

    void updateKaitenId(Email email, KaitenUserId kaitenId, String name, String avatarUrl);
}
