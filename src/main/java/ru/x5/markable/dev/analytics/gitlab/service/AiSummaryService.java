package ru.x5.markable.dev.analytics.gitlab.service;

import ru.x5.markable.dev.analytics.gitlab.rest.dto.AiSummaryDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.UserProfileDto;

/**
 * Сервис для генерации AI-саммари профилей пользователей.
 * 
 * <p>Предоставляет функциональность для автоматической генерации
 * текстовых описаний (саммари) профилей пользователей с использованием
 * искусственного интеллекта.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
public interface AiSummaryService {

    /**
     * Генерация AI саммари на основе профиля пользователя.
     * 
     * <p>Анализирует данные профиля пользователя, включая статистику коммитов,
     * активность по дням недели, задачи и другие метрики, и генерирует
     * текстовое описание с использованием AI.</p>
     * 
     * @param profile профиль пользователя с собранной статистикой
     * @return DTO сгенерированного AI-саммари
     */
    AiSummaryDto generateSummary(UserProfileDto profile);

}
