package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSummaryDto {
    private String taskNumber;
    private long commitCount;
    private LocalDateTime firstCommitDate;
    private LocalDateTime lastCommitDate;
}
