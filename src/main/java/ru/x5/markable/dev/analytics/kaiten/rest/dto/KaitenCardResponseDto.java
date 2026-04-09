package ru.x5.markable.dev.analytics.kaiten.rest.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class KaitenCardResponseDto {
    private Long id;
    private String title;
    private String description;
    private String status;
    private String priority;
    private String spaceName;
    private String boardName;
    private String ownerName;
    private String typeName;
    private String columnName;
    private String laneName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;
    private LocalDateTime lastMovedAt;
    private Boolean archived;
    private String tags;
    private String url;
}
