package ru.x5.markable.dev.analytics.gitlab.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "daily_author_stats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"email", "date"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyAuthorStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "merge_commits")
    private Long mergeCommits;

    @Column(name = "commits")
    private Long commits;

    @Column(name = "added_lines")
    private Long addedLines;

    @Column(name = "deleted_lines")
    private Long deletedLines;

    @Column(name = "test_added_lines")
    private Long testAddedLines;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "repository_name", nullable = false)
    private String repositoryName;

    @Column(name = "user_id")
    private Long userId;
}
