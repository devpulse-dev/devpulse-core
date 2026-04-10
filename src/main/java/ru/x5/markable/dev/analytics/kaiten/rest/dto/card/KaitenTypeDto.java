package ru.x5.markable.dev.analytics.kaiten.rest.dto.card;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO типа карточки Kaiten.
 * 
 * <p>Содержит информацию о типе карточки в системе Kaiten, включая идентификатор,
 * название, цвет, букву, идентификатор компании и статус архивации.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
public class KaitenTypeDto {
    
    /**
     * Идентификатор типа.
     * 
     * <p>Уникальный идентификатор типа карточки в системе Kaiten.</p>
     */
    private Long id;
    
    /**
     * Название типа.
     * 
     * <p>Название типа карточки.</p>
     */
    private String name;
    
    /**
     * Цвет типа.
     * 
     * <p>Цвет типа карточки в числовом формате.</p>
     */
    private Integer color;
    
    /**
     * Буква типа.
     * 
     * <p>Буква, обозначающая тип карточки.</p>
     */
    private String letter;

    /**
     * Идентификатор компании.
     * 
     * <p>Идентификатор компании, к которой относится тип карточки.</p>
     */
    @JsonProperty("company_id")
    private Long companyId;

    /**
     * Статус архивации.
     * 
     * <p>Признак архивации типа карточки.</p>
     */
    private Boolean archived;
}