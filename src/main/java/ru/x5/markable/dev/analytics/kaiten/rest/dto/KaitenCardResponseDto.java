package ru.x5.markable.dev.analytics.kaiten.rest.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * DTO ответа с информацией о карточке Kaiten.
 * 
 * <p>Содержит информацию о карточке Kaiten, включая её свойства,
 * статус, приоритет, владельца, доску, тип, колонку, дорожку, теги и URL.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
@Builder
public class KaitenCardResponseDto {
    
    /**
     * Идентификатор карточки.
     * 
     * <p>Уникальный идентификатор карточки в системе Kaiten.</p>
     */
    private Long id;
    
    /**
     * Заголовок карточки.
     * 
     * <p>Название или заголовок карточки.</p>
     */
    private String title;
    
    /**
     * Описание карточки.
     * 
     * <p>Описание карточки.</p>
     */
    private String description;
    
    /**
     * Статус карточки.
     * 
     * <p>Текущий статус карточки в системе Kaiten.</p>
     */
    private String status;
    
    /**
     * Приоритет карточки.
     * 
     * <p>Приоритет карточки в системе Kaiten.</p>
     */
    private String priority;
    
    /**
     * Название пространства.
     * 
     * <p>Название пространства, в котором находится карточка.</p>
     */
    private String spaceName;
    
    /**
     * Название доски.
     * 
     * <p>Название доски, на которой находится карточка.</p>
     */
    private String boardName;
    
    /**
     * Имя владельца.
     * 
     * <p>Имя владельца карточки.</p>
     */
    private String ownerName;
    
    /**
     * Название типа.
     * 
     * <p>Название типа карточки.</p>
     */
    private String typeName;
    
    /**
     * Название колонки.
     * 
     * <p>Название колонки, в которой находится карточка.</p>
     */
    private String columnName;
    
    /**
     * Название дорожки.
     * 
     * <p>Название дорожки, в которой находится карточка.</p>
     */
    private String laneName;
    
    /**
     * Дата создания.
     * 
     * <p>Дата и время создания карточки.</p>
     */
    private LocalDateTime createdAt;
    
    /**
     * Дата обновления.
     * 
     * <p>Дата и время последнего обновления карточки.</p>
     */
    private LocalDateTime updatedAt;
    
    /**
     * Дата закрытия.
     * 
     * <p>Дата и время закрытия карточки.</p>
     */
    private LocalDateTime closedAt;
    
    /**
     * Дата последнего перемещения.
     * 
     * <p>Дата и время последнего перемещения карточки.</p>
     */
    private LocalDateTime lastMovedAt;
    
    /**
     * Архивирована ли карточка.
     * 
     * <p>Флаг, указывающий, находится ли карточка в архиве.</p>
     */
    private Boolean archived;
    
    /**
     * Теги.
     * 
     * <p>Теги, связанные с карточкой.</p>
     */
    private String tags;
    
    /**
     * URL карточки.
     * 
     * <p>Ссылка на карточку в системе Kaiten.</p>
     */
    private String url;
}
