package ru.x5.devpulse.domain.model.performance;

/**
 * Счётчики карточек одного типа по статусу: в работе / закрыто в периоде / всего.
 *
 * <p>{@code total = inProgress + done} — карточки, пересекающиеся с периодом
 * (либо в работе сейчас, либо закрытые в периоде).</p>
 */
public record TaskStatusCounts(int inProgress, int done, int total) {

    public static final TaskStatusCounts EMPTY = new TaskStatusCounts(0, 0, 0);

    public static TaskStatusCounts of(int inProgress, int done) {
        return new TaskStatusCounts(inProgress, done, inProgress + done);
    }
}
