package ru.x5.markable.dev.analytics.kaiten.rest.dto.card;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO колонки Kaiten.
 * 
 * <p>Содержит информацию о колонке в системе Kaiten, включая идентификатор,
 * UID, название, порядок сортировки, количество, тип и идентификатор доски.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
public class KaitenColumnDto {
    
    /**
     * Идентификатор колонки.
     * 
     * <p>Уникальный идентификатор колонки в системе Kaiten.</p>
     */
    private Long id;
    
    /**
     * UID колонки.
     * 
     * <p>Уникальный идентификатор колонки в формате UID.</p>
     */
    private String uid;
    
    /**
     * Название колонки.
     * 
     * <p>Название или заголовок колонки.</p>
     */
    private String title;

    /**
     * Порядок сортировки.
     * 
     * <p>Порядок сортировки колонки на доске.</p>
     */
    @JsonProperty("sort_order")
    private Integer sortOrder;

    /**
     * Количество.
     * 
     * <p>Количество карточек в колонке.</p>
     */
    @JsonProperty("col_count")
    private Integer colCount;

    /**
     * Тип колонки.
     * 
     * <p>Тип колонки.</p>
     */
    private Integer type;

    /**
     * Идентификатор доски.
     * 
     * <p>Идентификатор доски, к которой относится колонка.</p>
     */
    @JsonProperty("board_id")
    private Long boardId;
}