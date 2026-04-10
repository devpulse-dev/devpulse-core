package ru.x5.markable.dev.analytics.kaiten.rest.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO ответа с информацией о пространстве Kaiten.
 * 
 * <p>Содержит информацию о пространстве в системе Kaiten.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
@Builder
public class KaitenSpaceResponseDto {
    
    /**
     * Идентификатор пространства.
     * 
     * <p>Уникальный идентификатор пространства в системе Kaiten.</p>
     */
    private Long id;
    
    /**
     * Заголовок пространства.
     * 
     * <p>Название или заголовок пространства.</p>
     */
    private String title;
    
    /**
     * Описание пространства.
     * 
     * <p>Описание пространства.</p>
     */
    private String description;
}
