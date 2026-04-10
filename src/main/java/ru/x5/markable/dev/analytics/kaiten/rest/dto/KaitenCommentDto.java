package ru.x5.markable.dev.analytics.kaiten.rest.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * DTO комментария Kaiten.
 * 
 * <p>Содержит информацию о комментарии к карточке Kaiten, включая автора,
 * текст комментария и даты создания и обновления.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
public class KaitenCommentDto {
    
    /**
     * Идентификатор комментария.
     * 
     * <p>Уникальный идентификатор комментария в системе Kaiten.</p>
     */
    private Long id;
    
    /**
     * Идентификатор карточки.
     * 
     * <p>Идентификатор карточки, к которой относится комментарий.</p>
     */
    private Long cardId;
    
    /**
     * Идентификатор автора.
     * 
     * <p>Идентификатор автора комментария.</p>
     */
    private Long authorId;
    
    /**
     * Имя автора.
     * 
     * <p>Имя автора комментария.</p>
     */
    private String authorName;
    
    /**
     * Email автора.
     * 
     * <p>Email адрес автора комментария.</p>
     */
    private String authorEmail;
    
    /**
     * Текст комментария.
     * 
     * <p>Содержание комментария.</p>
     */
    private String text;
    
    /**
     * Дата создания.
     * 
     * <p>Дата и время создания комментария.</p>
     */
    private LocalDateTime createdAt;
    
    /**
     * Дата обновления.
     * 
     * <p>Дата и время последнего обновления комментария.</p>
     */
    private LocalDateTime updatedAt;
}
