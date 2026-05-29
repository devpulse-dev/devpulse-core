package ru.x5.devpulse.domain.service;

import ru.x5.devpulse.domain.model.stats.ActivityCategory;
import ru.x5.devpulse.domain.model.stats.ActivityScore;
import ru.x5.devpulse.domain.model.stats.AuthorSummary;

/**
 * Расчёт {@link ActivityScore} разработчика за период.
 *
 * <p><b>Формула:</b> {@code score = volumeFactor × qualityFactor}, где:</p>
 * <ul>
 *   <li>{@code volumeFactor = nonMergeCommits / expectedCommits} — насколько активен относительно
 *       baseline команды (нормализуется под длину периода в use case'е).</li>
 *   <li>{@code qualityFactor} — поправочный коэффициент 0.3..1.0 по среднему числу строк
 *       на коммит:
 *       <ul>
 *         <li>0 строк → 0.3 (нет полезных правок)</li>
 *         <li>0..5 строк → linear ramp 0.3 → 0.7 (микро-коммиты, типа «typo fix»)</li>
 *         <li>5..10 строк → linear ramp 0.7 → 1.0</li>
 *         <li>10..200 строк → 1.0 (здоровый диапазон)</li>
 *         <li>200..500 → linear drop 1.0 → 0.8 (крупные коммиты, но допустимо)</li>
 *         <li>500..2000 → linear drop 0.8 → 0.5 («бомбы», плохо ревьюится)</li>
 *         <li>&gt; 2000 → 0.5 (полу-копипаст или генеренный код)</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p><b>Категории по score:</b> &lt;0.2 INACTIVE, &lt;0.6 BELOW_AVERAGE, &lt;1.5 ACTIVE, ≥1.5 STAR.</p>
 *
 * <p>Stateless utility — без I/O, чистая функция.</p>
 */
public final class ActivityScorer {

    private ActivityScorer() {}

    /**
     * Считает score для одного автора.
     *
     * @param author           агрегат активности
     * @param expectedCommits  baseline ожидаемых не-мердж коммитов для текущего периода
     *                         (50 коммитов / 30 дней, отмасштабированное под длину периода)
     */
    public static ActivityScore score(AuthorSummary author, double expectedCommits) {
        double base = Math.max(1.0, expectedCommits);
        double volume = author.nonMergeCommits() / base;
        long totalLines = author.addedLines() + author.deletedLines();
        double avg = author.nonMergeCommits() > 0
                ? (double) totalLines / author.nonMergeCommits()
                : 0.0;
        double quality = qualityFactor(avg);
        double finalScore = volume * quality;
        return new ActivityScore(finalScore, categorize(finalScore), volume, quality, avg);
    }

    /** См. таблицу в Javadoc класса. */
    static double qualityFactor(double avgLinesPerCommit) {
        if (avgLinesPerCommit <= 0) return 0.3;
        if (avgLinesPerCommit < 5)    return 0.3 + (avgLinesPerCommit / 5.0) * 0.4;            // 0.3 → 0.7
        if (avgLinesPerCommit < 10)   return 0.7 + ((avgLinesPerCommit - 5) / 5.0) * 0.3;      // 0.7 → 1.0
        if (avgLinesPerCommit <= 200) return 1.0;
        if (avgLinesPerCommit <= 500) return 1.0 - ((avgLinesPerCommit - 200) / 300.0) * 0.2;  // 1.0 → 0.8
        if (avgLinesPerCommit <= 2000) return 0.8 - ((avgLinesPerCommit - 500) / 1500.0) * 0.3; // 0.8 → 0.5
        return 0.5;
    }

    static ActivityCategory categorize(double score) {
        if (score < 0.2) return ActivityCategory.INACTIVE;
        if (score < 0.6) return ActivityCategory.BELOW_AVERAGE;
        if (score < 1.5) return ActivityCategory.ACTIVE;
        return ActivityCategory.STAR;
    }
}
