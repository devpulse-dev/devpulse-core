package ru.x5.markable.dev.analytics.application.port.in;

import java.time.LocalDateTime;
import ru.x5.markable.dev.analytics.domain.model.collection.CollectionRun;

/**
 * Запуск сбора статистики (Git → save → Kaiten).
 *
 * <p>Реализация в application-слое; запускается планировщиком в bootstrap и REST-эндпоинтом
 * {@code POST /api/v2/collection/runs}.</p>
 */
public interface CollectDailyStatsUseCase {

    /**
     * Собирает за период {@code [since..now]}.
     * Если {@code since == null} — стартует с момента последней успешной выгрузки.
     *
     * @return запись о запуске со статусом SUCCESS/FAILED
     */
    CollectionRun run(LocalDateTime since);
}
