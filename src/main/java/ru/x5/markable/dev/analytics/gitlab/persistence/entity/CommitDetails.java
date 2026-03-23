package ru.x5.markable.dev.analytics.gitlab.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "commit_details")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "commit_hash", nullable = false, unique = true)
    private String commitHash;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "commit_date", nullable = false)
    private LocalDateTime commitDate;

    @Column(name = "hour")
    private Integer hour;

    @Column(name = "is_merge")
    private boolean isMerge;

    @Column(name = "task_number")
    private String taskNumber;

    @Column(name = "commit_message")
    private String commitMessage;

    @Column(name = "added_lines")
    private long addedLines;

    @Column(name = "deleted_lines")
    private long deletedLines;

    @Column(name = "test_added_lines")
    private long testAddedLines;

    @Column(name = "repository_name")
    private String repositoryName;

    @Column(name = "collected_at")
    private LocalDateTime collectedAt;
}
