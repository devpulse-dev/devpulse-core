package ru.x5.markable.dev.analytics.kaiten.rest.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class KaitenCommentDto {
    private Long id;
    private Long cardId;
    private Long authorId;
    private String authorName;
    private String authorEmail;
    private String text;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
