package ru.x5.devpulse.domain.model.performance;

/**
 * Разбивка карточек Kaiten по типу (дефект / разработка) и статусу. Снапшот «как сейчас».
 */
public record TaskTypeBreakdown(TaskStatusCounts defect, TaskStatusCounts development) {

    public static final TaskTypeBreakdown EMPTY =
            new TaskTypeBreakdown(TaskStatusCounts.EMPTY, TaskStatusCounts.EMPTY);
}
