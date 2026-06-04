package ru.x5.devpulse.domain.model.review;

import java.util.Objects;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Участие одного ревьюера в одном MR: факт approve + объём комментариев.
 *
 * <p>Одна запись на пару {@code (MR × ревьюер)}. Самокомментарии/самоапрув автора MR
 * на этом уровне уже отфильтрованы при сборе — сюда попадает только чужое ревью.</p>
 *
 * @param reviewer     ревьюер (email, нормализован)
 * @param approved     нажал ли Approve по этому MR
 * @param commentCount число ревью-комментариев этого ревьюера к этому MR (объём, ≥ 0)
 */
public record MrReview(Email reviewer, boolean approved, int commentCount) {

    public MrReview {
        Objects.requireNonNull(reviewer, "reviewer required");
        if (commentCount < 0) {
            throw new IllegalArgumentException("commentCount must be non-negative, got " + commentCount);
        }
    }
}
