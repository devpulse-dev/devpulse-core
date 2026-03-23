package ru.x5.markable.dev.analytics.gitlab.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitDetail {
    private String hash;
    private String email;
    private LocalDateTime commitDate;
    private boolean isMerge;
    private long added;
    private long deleted;
    private long testAdded;
    private String repoName;
    private String taskNumber;
    private String commitMessage;
}

