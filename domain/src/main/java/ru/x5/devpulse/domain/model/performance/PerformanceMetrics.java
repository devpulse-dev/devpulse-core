package ru.x5.devpulse.domain.model.performance;

/**
 * Набор метрик досье к perf-review, каждая — с дельтой к предыдущему периоду
 * ({@link MetricDelta}). Собирается {@code PerformanceReviewAssembler}.
 *
 * <p>{@code *InWork}/{@code *Closed} — карточные, снапшот «как сейчас» (без дельты).</p>
 */
public record PerformanceMetrics(
        MetricDelta commits,
        MetricDelta nonMergeCommits,
        MetricDelta addedLines,
        MetricDelta deletedLines,
        MetricDelta testAddedLines,
        MetricDelta reviewsGiven,
        MetricDelta commentsGiven,
        MetricDelta reviewsReceived,
        MetricDelta avgTimeToMergeHours,
        MetricDelta mergedMrCount,
        MetricDelta defectsInWork,
        MetricDelta defectsClosed,
        MetricDelta devTasksInWork,
        MetricDelta devTasksClosed
) {}
