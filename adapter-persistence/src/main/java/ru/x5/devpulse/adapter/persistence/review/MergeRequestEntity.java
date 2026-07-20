package ru.x5.devpulse.adapter.persistence.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "merge_request",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_merge_request_project_iid",
                columnNames = {"gitlab_project_id", "gitlab_mr_iid"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergeRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gitlab_project_id", nullable = false)
    private Long gitlabProjectId;

    @Column(name = "gitlab_mr_iid", nullable = false)
    private Long gitlabMrIid;

    @Column(name = "author_email", nullable = false)
    private String authorEmail;

    @Column(name = "title", length = 1000)
    private String title;

    @Column(name = "web_url", length = 1000)
    private String webUrl;

    @Column(name = "state", nullable = false, length = 20)
    private String state;

    /** Ветка назначения MR (target_branch). Nullable: у собранных до миграции 030 её нет. */
    @Column(name = "target_branch", length = 255)
    private String targetBranch;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "merged_at")
    private LocalDateTime mergedAt;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;
}
