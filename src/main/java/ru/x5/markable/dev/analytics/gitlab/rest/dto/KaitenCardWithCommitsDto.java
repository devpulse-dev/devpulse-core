package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO для карточки Kaiten с коммитами.
 * 
 * <p>Содержит информацию о карточке Kaiten и связанных с ней коммитах.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
@Builder
public class KaitenCardWithCommitsDto {
    
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
     * Дата создания.
     * 
     * <p>Дата и время создания карточки.</p>
     */
    private LocalDateTime createdAt;
    
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
     * URL карточки.
     * 
     * <p>Ссылка на карточку в системе Kaiten.</p>
     */
    private String url;
    
    /**
     * Список коммитов.
     * 
     * <p>Список коммитов, связанных с карточкой.</p>
     */
    private List<CommitDetailDto> commits;
}