package ru.x5.devpulse.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.x5.devpulse.application.port.in.SyncKaitenUsersUseCase;
import ru.x5.devpulse.application.port.out.KaitenGateway;
import ru.x5.devpulse.application.port.out.KaitenUserRepository;
import ru.x5.devpulse.domain.model.kaiten.KaitenUser;

/**
 * Реализация {@link SyncKaitenUsersUseCase}.
 *
 * <p>Тянет весь список пользователей Kaiten через HTTP-шлюз и bulk-upsert-ит в локальную БД.
 * Без транзакций на этом слое — границу транзакции рисует адаптер-репозиторий.</p>
 */
@Slf4j
@RequiredArgsConstructor
public final class SyncKaitenUsersService implements SyncKaitenUsersUseCase {

    private final KaitenGateway kaitenGateway;
    private final KaitenUserRepository kaitenUserRepository;

    @Override
    public int syncAll() {
        log.info("Старт синхронизации пользователей Kaiten");
        List<KaitenUser> users = kaitenGateway.fetchAllUsers();
        if (users.isEmpty()) {
            log.warn("Kaiten вернул пустой список пользователей — пропускаем upsert");
            return 0;
        }
        kaitenUserRepository.upsertAll(users);
        log.info("Синхронизация Kaiten users завершена: {} записей", users.size());
        return users.size();
    }
}
