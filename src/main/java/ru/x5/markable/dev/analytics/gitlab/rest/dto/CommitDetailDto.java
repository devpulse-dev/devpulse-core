package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.CommitDetails;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitDetailDto {
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

    public static CommitDetailDto fromEntity(CommitDetails entity) {
        return CommitDetailDto.builder()
                .hash(entity.getCommitHash())
                .email(entity.getEmail())
                .commitDate(entity.getCommitDate())
                .isMerge(entity.isMerge())
                .added(entity.getAddedLines())
                .deleted(entity.getDeletedLines())
                .testAdded(entity.getTestAddedLines())
                .repoName(entity.getRepositoryName())
                .taskNumber(entity.getTaskNumber())
                .commitMessage(entity.getCommitMessage())
                .build();
    }
}
