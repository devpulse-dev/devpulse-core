package ru.x5.devpulse.application.port.in;

import java.time.LocalDateTime;
import java.util.Set;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Сбор git-статистики за период: stream commits, cleanup rebase-zombies, recompute daily_stats.
 *
 * <p>Этот use case — <b>worker</b>, его дёргает orchestrator
 * ({@code CollectDailyStatsUseCase}). Он не знает про CollectionRun lifecycle и distributed
 * lock — его задача только git-фаза.</p>
 *
 * <p>Это разделение появилось в рамках review-фикса #15: исходный
 * {@code CollectDailyStatsService} имел 8 зависимостей и делал три не связанных дела.
 * Здесь — только git (5 зависимостей).</p>
 */
public interface CollectGitStatsUseCase {

    /**
     * Прогоняет git-фазу по всем сконфигурированным репозиториям.
     *
     * @param since  начало периода (включительно)
     * @param until  конец периода
     * @param cancel сигнал кооперативной отмены — проверяется перед каждым репозиторием;
     *               при отмене обход прекращается, recompute пропускается, бросается
     *               внутренний cancel-сигнал (ловит оркестратор → CANCELLED)
     * @return email'ы всех затронутых авторов (для трассировки/метрик); пустое — если
     *         репозиториев нет или ни одного коммита не пришло
     */
    Set<Email> collect(LocalDateTime since, LocalDateTime until, CancellationSignal cancel);
}
