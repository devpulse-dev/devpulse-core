package ru.x5.markable.dev.analytics.kaiten.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.card.KaitenBoardDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.card.KaitenColumnDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.card.KaitenLaneDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.card.KaitenMemberDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.card.KaitenOwnerDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.card.KaitenTagDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.card.KaitenTypeDto;

/**
 * DTO карточки Kaiten.
 * 
 * <p>Содержит полную информацию о карточке Kaiten, включая её свойства,
 * владельца, доску, тип, колонку, дорожку, теги и участников.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
public class KaitenCardDto {
    
    /**
     * Идентификатор карточки.
     * 
     * <p>Уникальный идентификатор карточки в системе Kaiten.</p>
     */
    private Long id;
    
    /**
     * UID карточки.
     * 
     * <p>Уникальный идентификатор карточки в формате UID.</p>
     */
    private String uid;

    /**
     * Дата создания.
     * 
     * <p>Дата и время создания карточки.</p>
     */
    @JsonProperty("created")
    private LocalDateTime createdAt;

    /**
     * Дата обновления.
     * 
     * <p>Дата и время последнего обновления карточки.</p>
     */
    @JsonProperty("updated")
    private LocalDateTime updatedAt;

    /**
     * Архивирована ли карточка.
     * 
     * <p>Флаг, указывающий, находится ли карточка в архиве.</p>
     */
    private Boolean archived;
    
    /**
     * Заголовок карточки.
     * 
     * <p>Название или заголовок карточки.</p>
     */
    private String title;

    /**
     * Дата последнего перемещения.
     * 
     * <p>Дата и время последнего перемещения карточки.</p>
     */
    @JsonProperty("last_moved_at")
    private LocalDateTime lastMovedAt;

    /**
     * Дата изменения дорожки.
     * 
     * <p>Дата и время последнего изменения дорожки карточки.</p>
     */
    @JsonProperty("lane_changed_at")
    private LocalDateTime laneChangedAt;

    /**
     * Свойства карточки.
     * 
     * <p>Карта дополнительных свойств карточки.</p>
     */
    private Map<String, Object> properties;

    // Вложенные объекты
    
    /**
     * Владелец карточки.
     * 
     * <p>Информация о владельце карточки.</p>
     */
    private KaitenOwnerDto owner;
    
    /**
     * Доска.
     * 
     * <p>Информация о доске, на которой находится карточка.</p>
     */
    private KaitenBoardDto board;
    
    /**
     * Тип карточки.
     * 
     * <p>Информация о типе карточки.</p>
     */
    private KaitenTypeDto type;
    
    /**
     * Колонка.
     * 
     * <p>Информация о колонке, в которой находится карточка.</p>
     */
    private KaitenColumnDto column;
    
    /**
     * Дорожка.
     * 
     * <p>Информация о дорожке, в которой находится карточка.</p>
     */
    private KaitenLaneDto lane;
    
    /**
     * Теги.
     * 
     * <p>Список тегов, связанных с карточкой.</p>
     */
    private List<KaitenTagDto> tags;

    // Дополнительные поля
    
    /**
     * Идентификатор доски.
     * 
     * <p>Идентификатор доски, на которой находится карточка.</p>
     */
    @JsonProperty("board_id")
    private Long boardId;

    /**
     * Идентификатор колонки.
     * 
     * <p>Идентификатор колонки, в которой находится карточка.</p>
     */
    @JsonProperty("column_id")
    private Long columnId;

    /**
     * Идентификатор дорожки.
     * 
     * <p>Идентификатор дорожки, в которой находится карточка.</p>
     */
    @JsonProperty("lane_id")
    private Long laneId;

    /**
     * Идентификатор владельца.
     * 
     * <p>Идентификатор владельца карточки.</p>
     */
    @JsonProperty("owner_id")
    private Long ownerId;

    /**
     * Идентификатор типа.
     * 
     * <p>Идентификатор типа карточки.</p>
     */
    @JsonProperty("type_id")
    private Long typeId;

    /**
     * Состояние.
     * 
     * <p>Состояние карточки.</p>
     */
    private Integer state;
    
    /**
     * Условие.
     * 
     * <p>Условие карточки.</p>
     */
    private Integer condition;
    
    /**
     * Участники.
     * 
     * <p>Список участников карточки.</p>
     */
    private List<KaitenMemberDto> members;
}