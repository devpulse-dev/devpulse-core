package ru.x5.devpulse.domain.model.collection;

/**
 * Состояние одного запуска сбора (Git + Kaiten).
 */
public enum CollectionStatus {
    /** Сбор инициирован, идёт прямо сейчас. */
    RUNNING,
    /** Все этапы успешно завершены. */
    SUCCESS,
    /** Прерван ошибкой; см. {@code errorMessage} в {@link CollectionRun}. */
    FAILED,
    /**
     * Отменён оператором (POST /collection/runs/{id}/cancel). Семантика консистентности
     * как у {@link #FAILED}: курсор не двигается, следующий сбор доберёт недостающее.
     */
    CANCELLED;

    /** Терминальный ли статус (сбор завершён — успехом, ошибкой или отменой). */
    public boolean isTerminal() {
        return this != RUNNING;
    }
}
