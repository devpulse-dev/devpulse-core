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

/**
 * Участие одного ревьюера в одном MR: {@code approved} + {@code commentCount} (объём комментов).
 * Одна строка на пару {@code (merge_request_id × reviewer_email)}.
 */
@Entity
@Table(
        name = "mr_review",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mr_review_mr_reviewer",
                columnNames = {"merge_request_id", "reviewer_email"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MrReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merge_request_id", nullable = false)
    private Long mergeRequestId;

    @Column(name = "reviewer_email", nullable = false)
    private String reviewerEmail;

    @Column(name = "approved", nullable = false)
    private boolean approved;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;
}
