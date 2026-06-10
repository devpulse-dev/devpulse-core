package ru.x5.devpulse.application.port.out;

/**
 * Port out: запуск задачи в фоне (вне текущего/request-потока).
 *
 * <p>Application Spring-free и не может использовать {@code @Async} — фоновое исполнение
 * инвертируется в этот порт (как {@link TransactionRunner} для транзакций). Реализация в
 * bootstrap поверх virtual-thread executor'а (НЕ Spring-managed lifecycle, чтобы graceful
 * shutdown не ждал многочасовой сбор; прерванный на остановке прогон подхватит
 * startup-реконсиляция — ADR-13).</p>
 *
 * <p>Используется оркестратором сбора: POST отдаёт 202 синхронно, сам сбор уходит сюда.</p>
 */
public interface BackgroundRunner {

    /** Запускает задачу асинхронно и немедленно возвращает управление. */
    void run(Runnable task);
}
