package ru.x5.markable.dev.analytics.kaiten.rest.dto.card;

import lombok.Data;

/**
 * DTO тега Kaiten.
 * 
 * <p>Содержит информацию о теге в системе Kaiten, включая идентификатор,
 * название и цвет.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
public class KaitenTagDto {
    
    /**
     * Идентификатор тега.
     * 
     * <p>Уникальный идентификатор тега в системе Kaiten.</p>
     */
    private Long id;
    
    /**
     * Название тега.
     * 
     * <p>Название или имя тега.</p>
     */
    private String name;
    
    /**
     * Цвет тега.
     * 
     * <p>Цвет тега в числовом формате.</p>
     */
    private Integer color;
}