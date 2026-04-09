package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class KaitenCardWithCommitsDto {
    private Long id;
    private String title;
    private String status;
    private String priority;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;
    private LocalDateTime lastMovedAt;
    private String url;
    private List<CommitDetailDto> commits;
}