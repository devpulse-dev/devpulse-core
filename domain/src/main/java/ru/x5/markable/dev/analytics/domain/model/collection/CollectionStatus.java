package ru.x5.markable.dev.analytics.domain.model.collection;

/**
 * Состояние одного запуска сбора (Git + Kaiten).
 */
public enum CollectionStatus {
    /** Сбор инициирован, идёт прямо сейчас. */
    RUNNING,
    /** Все этапы успешно завершены. */
    SUCCESS,
    /** Прерван ошибкой; см. {@code errorMessage} в {@link CollectionRun}. */
    FAILED
}
