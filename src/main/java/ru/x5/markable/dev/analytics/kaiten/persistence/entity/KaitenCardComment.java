package ru.x5.markable.dev.analytics.kaiten.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Сущность для хранения комментариев к карточкам Kaiten.
 * 
 * <p>Содержит информацию о комментарии, включая текст, автора,
 * дату создания и обновления, а также ссылку на карточку.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Entity
@Table(name = "kaiten_card_comment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KaitenCardComment {

    /**
     * Уникальный идентификатор комментария.
     */
    @Id
    private Long id;

    /**
     * Идентификатор карточки, к которой относится комментарий.
     */
    @Column(name = "card_id", nullable = false)
    private Long cardId;

    /**
     * Идентификатор автора комментария.
     */
    @Column(name = "author_id")
    private Long authorId;

    /**
     * Имя автора комментария.
     */
    @Column(name = "author_name")
    private String authorName;

    /**
     * Текст комментария.
     */
    @Column(name = "text", columnDefinition = "TEXT")
    private String text;

    /**
     * Дата и время создания комментария.
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Дата и время последнего обновления комментария.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
