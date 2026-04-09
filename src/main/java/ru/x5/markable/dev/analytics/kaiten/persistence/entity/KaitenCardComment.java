package ru.x5.markable.dev.analytics.kaiten.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "kaiten_card_comment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KaitenCardComment {

    @Id
    private Long id;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "text", columnDefinition = "TEXT")
    private String text;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
