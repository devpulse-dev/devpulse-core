package ru.x5.devpulse.domain.model.performance;

/**
 * Доставленная доработка: корневая задача с завершёнными юскейсами.
 *
 * @param doneCount  сколько юскейсов закрыто (status == DONE)
 * @param totalCount всего юскейсов под этой корневой задачей
 */
public record DeliveredFeature(long id, String title, String url, int doneCount, int totalCount) {}
