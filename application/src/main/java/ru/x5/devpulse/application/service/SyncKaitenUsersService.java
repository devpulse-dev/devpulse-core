package ru.x5.devpulse.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.x5.devpulse.application.port.in.SyncKaitenUsersUseCase;
import ru.x5.devpulse.application.port.out.KaitenGateway;
import ru.x5.devpulse.application.port.out.KaitenUserRepository;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.model.kaiten.KaitenUser;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Реализация {@link SyncKaitenUsersUseCase}.
 *
 * <p><b>Полный цикл sync'а:</b></p>
 * <ol>
 *   <li>{@code kaitenGateway.fetchAllUsers()} — тянет всех пользователей Kaiten;</li>
 *   <li>{@code kaitenUserRepository.upsertAll(...)} — bulk-upsert в {@code kaiten_user};</li>
 *   <li>Для каждого пользователя с непустым email — {@code unifiedUserRepository.updateKaitenId(...)}
 *       пробрасывает {@code kaiten_id}/{@code name}/{@code avatar_url} в {@code unified_user}.</li>
 * </ol>
 *
 * <p>Без явных транзакций на этом слое: каждый adapter-метод сам {@code @Transactional}.
 * Между upsert kaiten_user и updateKaitenId возможен partial state, если процесс упадёт
 * посреди цикла — но retry идемпотентен (upsert не дублирует, updateKaitenId по email).</p>
 */
@Slf4j
@RequiredArgsConstructor
public final class SyncKaitenUsersService implements SyncKaitenUsersUseCase {

    private final KaitenGateway kaitenGateway;
    private final KaitenUserRepository kaitenUserRepository;
    private final UnifiedUserRepository unifiedUserRepository;

    @Override
    public int syncAll() {
        log.info("Старт синхронизации пользователей Kaiten");
        List<KaitenUser> users = kaitenGateway.fetchAllUsers();
        if (users.isEmpty()) {
            log.warn("Kaiten вернул пустой список пользователей — пропускаем upsert");
            return 0;
        }
        kaitenUserRepository.upsertAll(users);

        int linked = 0;
        for (KaitenUser u : users) {
            Email email = u.email();
            if (email == null) continue;
            unifiedUserRepository.updateKaitenId(email, u.id(), u.fullName(), u.avatarUrl());
            linked++;
        }
        log.info("Синхронизация Kaiten users завершена: upserted {}, привязано к unified_user {}",
                users.size(), linked);
        return users.size();
    }
}
