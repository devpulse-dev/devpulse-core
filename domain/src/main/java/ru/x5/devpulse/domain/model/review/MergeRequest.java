package ru.x5.devpulse.domain.model.review;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Merge request с участием ревьюеров — read-side модель для агрегации ревью-метрик.
 *
 * @param author    автор MR (email, нормализован)
 * @param createdAt момент открытия MR
 * @param mergedAt  момент merge; {@code null} если не смержен (open/closed)
 * @param reviews   участие ревьюеров (approve + комментарии); пусто — никто не ревьюил
 */
public record MergeRequest(
        Email author,
        LocalDateTime createdAt,
        LocalDateTime mergedAt,
        List<MrReview> reviews
) {

    public MergeRequest {
        Objects.requireNonNull(author, "author required");
        Objects.requireNonNull(createdAt, "createdAt required");
        reviews = reviews == null ? List.of() : List.copyOf(reviews);
    }

    public boolean isMerged() {
        return mergedAt != null;
    }

    public boolean wasReviewed() {
        return !reviews.isEmpty();
    }
}
