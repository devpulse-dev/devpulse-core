package ru.x5.markable.dev.analytics.kaiten.rest.dto.card;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO доски Kaiten.
 * 
 * <p>Содержит информацию о доске в системе Kaiten, включая идентификатор,
 * UID, название и внешний идентификатор.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
public class KaitenBoardDto {
    
    /**
     * Идентификатор доски.
     * 
     * <p>Уникальный идентификатор доски в системе Kaiten.</p>
     */
    private Long id;
    
    /**
     * UID доски.
     * 
     * <p>Уникальный идентификатор доски в формате UID.</p>
     */
    private String uid;
    
    /**
     * Название доски.
     * 
     * <p>Название или заголовок доски.</p>
     */
    private String title;

    /**
     * Внешний идентификатор.
     * 
     * <p>Внешний идентификатор доски.</p>
     */
    @JsonProperty("external_id")
    private String externalId;
}