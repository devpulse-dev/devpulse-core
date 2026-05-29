package ru.x5.devpulse.application.port.in;

/**
 * Принудительная синхронизация пользователей Kaiten в локальную БД.
 * Обслуживает {@code POST /api/v2/kaiten/sync-users}.
 */
public interface SyncKaitenUsersUseCase {

    /**
     * @return число синхронизированных (обновлённых + созданных) пользователей
     */
    int syncAll();
}
