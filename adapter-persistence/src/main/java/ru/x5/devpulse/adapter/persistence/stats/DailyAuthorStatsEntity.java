package ru.x5.devpulse.adapter.persistence.stats;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "daily_author_stats",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_stats_email_date_repo",
                columnNames = {"email", "date", "repository_name"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyAuthorStatsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "repository_name", nullable = false)
    private String repositoryName;

    @Column(name = "commits", nullable = false)
    private long commits;

    @Column(name = "merge_commits")
    private long mergeCommits;

    @Column(name = "added_lines", nullable = false)
    private long addedLines;

    @Column(name = "deleted_lines", nullable = false)
    private long deletedLines;

    @Column(name = "test_added_lines")
    private long testAddedLines;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Column(name = "user_id")
    private Long userId;
}
