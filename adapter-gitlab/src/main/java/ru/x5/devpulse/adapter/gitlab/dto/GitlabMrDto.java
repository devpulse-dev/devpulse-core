package ru.x5.devpulse.adapter.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/**
 * Merge request GitLab (список {@code /merge_requests}).
 *
 * @param userNotesCount число <b>пользовательских</b> (не системных) комментариев — GitLab считает
 *                       его ровно по тому же критерию, по которому мы фильтруем notes
 *                       ({@code system=false}). Даёт пропустить запрос {@code /notes} для MR без
 *                       обсуждения: на per-MR вызовах стоит весь бюджет RPS, а таких MR в крупном
 *                       репозитории — заметная доля. {@code null} (поля нет в ответе) трактуется
 *                       как «неизвестно» → notes запрашиваются, как раньше.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitlabMrDto(
        Long id,
        Long iid,
        @JsonProperty("project_id") Long projectId,
        String title,
        String state,
        @JsonProperty("web_url") String webUrl,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("merged_at") OffsetDateTime mergedAt,
        @JsonProperty("target_branch") String targetBranch,
        GitlabUserDto author,
        @JsonProperty("user_notes_count") Integer userNotesCount
) {

    /**
     * Точно ли известно, что пользовательских комментариев нет. {@code false} при {@code null} —
     * незнание трактуем в пользу запроса (лучше лишний вызов, чем потерянные ревью-метрики).
     */
    public boolean hasNoUserNotes() {
        return userNotesCount != null && userNotesCount == 0;
    }
}
