package ru.x5.markable.dev.analytics.kaiten.rest.dto.card;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO дорожки (lane) Kaiten.
 * 
 * <p>Содержит информацию о дорожке в системе Kaiten, включая идентификатор,
 * UID, название, порядок сортировки, идентификатор доски и условие.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
public class KaitenLaneDto {
    
    /**
     * Идентификатор дорожки.
     * 
     * <p>Уникальный идентификатор дорожки в системе Kaiten.</p>
     */
    private Long id;
    
    /**
     * UID дорожки.
     * 
     * <p>Уникальный идентификатор дорожки в формате UID.</p>
     */
    private String uid;
    
    /**
     * Название дорожки.
     * 
     * <p>Название или заголовок дорожки.</p>
     */
    private String title;

    /**
     * Порядок сортировки.
     * 
     * <p>Порядок сортировки дорожки на доске.</p>
     */
    @JsonProperty("sort_order")
    private Integer sortOrder;

    /**
     * Идентификатор доски.
     * 
     * <p>Идентификатор доски, к которой относится дорожка.</p>
     */
    @JsonProperty("board_id")
    private Long boardId;

    /**
     * Условие.
     * 
     * <p>Условие, определяющее поведение дорожки.</p>
     */
    private Integer condition;
}