package ru.x5.devpulse.adapter.rest.dto;

import ru.x5.devpulse.domain.model.stats.ActivityScore;

/**
 * Активность разработчика для REST-ответов: финальный score + категория + разбивка.
 *
 * @param score              финал (1.0 = норма команды; см. baseline в конфиге)
 * @param category           бакет: INACTIVE / BELOW_AVERAGE / ACTIVE / STAR
 * @param volumeFactor       объёмная составляющая: nonMergeCommits / expectedCommits
 * @param qualityFactor      качественная: 0.3..1.0 от среднего числа строк на коммит
 * @param avgLinesPerCommit  для тултипа на фронте
 */
public record ActivityScoreResponse(
        double score,
        String category,
        double volumeFactor,
        double qualityFactor,
        double avgLinesPerCommit
) {
    public static ActivityScoreResponse from(ActivityScore s) {
        return new ActivityScoreResponse(
                round(s.score()),
                s.category().name(),
                round(s.volumeFactor()),
                round(s.qualityFactor()),
                round(s.avgLinesPerCommit()));
    }

    /** До 3 знаков после запятой — фронту приятнее чем {@code 0.6857142857142857}. */
    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
