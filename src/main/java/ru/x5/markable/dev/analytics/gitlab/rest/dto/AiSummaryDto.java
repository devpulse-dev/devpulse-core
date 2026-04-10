package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для ответа с AI-суммаризацией.
 * 
 * <p>Содержит сгенерированную AI-моделью сводку и метаданные о генерации.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSummaryDto {
    
    /**
     * Сгенерированная AI-моделью сводка.
     * 
     * <p>Содержит краткое описание работы пользователя за указанный период.</p>
     */
    private String summary;
    
    /**
     * Время генерации в миллисекундах.
     * 
     * <p>Показывает, сколько времени заняла генерация сводки AI-моделью.</p>
     */
    private long generationTimeMs;
    
    /**
     * Название использованной AI-модели.
     * 
     * <p>Указывает, какая модель была использована для генерации сводки.</p>
     */
    private String model;
}
