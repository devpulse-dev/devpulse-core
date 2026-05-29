package ru.x5.devpulse.application.port.out;

/**
 * Port out: распределённый mutex на запуск {@link
 * ru.x5.devpulse.application.port.in.CollectDailyStatsUseCase}.
 *
 * <p>Гарантирует что в любой момент времени в системе (включая параллельные инстансы) идёт
 * не более одного сбора. Реализация может опираться на Postgres advisory lock или другую
 * shared-state-based блокировку.</p>
 *
 * <p>Семантика — non-blocking: если занято, бросаем {@link CollectionAlreadyRunningException}
 * вместо ожидания. Это сознательно: пользователь, ткнувший «собрать» второй раз, должен сразу
 * увидеть 409, а не получить ответ через час.</p>
 */
public interface CollectionLock {

    /**
     * Пытается взять lock.
     *
     * @return handle, который ОБЯЗАН быть закрыт через {@code try-with-resources}
     * @throws CollectionAlreadyRunningException если lock занят другим сбором
     */
    Handle acquireOrThrow();

    /**
     * Handle взятого lock'а. {@link #close()} освобождает lock и
     * отпускает удерживаемые ресурсы (например, JDBC connection).
     */
    interface Handle extends AutoCloseable {
        @Override
        void close();
    }
}
