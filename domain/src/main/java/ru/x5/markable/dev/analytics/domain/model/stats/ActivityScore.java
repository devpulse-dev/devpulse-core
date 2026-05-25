package ru.x5.markable.dev.analytics.domain.model.stats;

import java.util.Objects;

/**
 * Score активности разработчика за период с разложением.
 *
 * <p>Финал: {@code score = volumeFactor × qualityFactor}.</p>
 *
 * @param score              итоговый коэффициент (1.0 = норма команды)
 * @param category           бакет по диапазонам score
 * @param volumeFactor       объём: nonMergeCommits / expectedCommits (под текущий период)
 * @param qualityFactor      качество: 0..1, штраф за «микро-» и «бомба»-коммиты
 * @param avgLinesPerCommit  средняя длина коммита в строках — для отображения в тултипе
 */
public record ActivityScore(
        double score,
        ActivityCategory category,
        double volumeFactor,
        double qualityFactor,
        double avgLinesPerCommit
) {

    public ActivityScore {
        Objects.requireNonNull(category, "category required");
    }
}
