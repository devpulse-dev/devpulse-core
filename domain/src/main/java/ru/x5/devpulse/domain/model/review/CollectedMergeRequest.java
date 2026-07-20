package ru.x5.devpulse.domain.model.review;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * MR, собранный из GitLab для записи в БД (collection-сторона). В отличие от read-модели
 * {@link MergeRequest}, несёт идентификаторы GitLab и метаданные для upsert.
 *
 * <p>Email автора и ревьюеров уже разрезолвлены адаптером (public_email или
 * {@code username@домен}); саморевью отфильтрованы (reviewer ≠ author).</p>
 *
 * @param gitlabProjectId id проекта в GitLab
 * @param gitlabMrIid     iid MR внутри проекта
 * @param author          автор MR (email)
 * @param title           заголовок MR
 * @param webUrl          ссылка на MR
 * @param state           состояние GitLab: opened / merged / closed / locked
 * @param createdAt       момент открытия
 * @param mergedAt        момент merge ({@code null} если не смержен)
 * @param targetBranch    ветка назначения MR ({@code null} у исторических записей до сбора ветки)
 * @param reviews         участие ревьюеров (approve + объём комментов)
 */
public record CollectedMergeRequest(
        long gitlabProjectId,
        long gitlabMrIid,
        Email author,
        String title,
        String webUrl,
        String state,
        LocalDateTime createdAt,
        LocalDateTime mergedAt,
        String targetBranch,
        List<MrReview> reviews
) {

    public CollectedMergeRequest {
        Objects.requireNonNull(author, "author required");
        Objects.requireNonNull(createdAt, "createdAt required");
        reviews = reviews == null ? List.of() : List.copyOf(reviews);
    }
}
