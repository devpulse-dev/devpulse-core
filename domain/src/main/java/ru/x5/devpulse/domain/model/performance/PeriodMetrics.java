package ru.x5.devpulse.domain.model.performance;

/**
 * Сырые (скалярные) метрики одного человека за один период — только историчные источники
 * (git + ревью), по которым корректно считать дельту между периодами.
 *
 * <p>Карточные метрики (дефекты/задачи) сюда НЕ входят: карточки Kaiten не персистятся,
 * считаются снапшотом «как сейчас» и дельты не имеют (см. future.md).</p>
 */
public record PeriodMetrics(
        long commits,
        long nonMergeCommits,
        long addedLines,
        long deletedLines,
        long testAddedLines,
        int reviewsGiven,
        int commentsGiven,
        int reviewsReceived,
        double avgTimeToMergeHours,
        int mergedMrCount
) {
    public static final PeriodMetrics EMPTY =
            new PeriodMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0);
}
