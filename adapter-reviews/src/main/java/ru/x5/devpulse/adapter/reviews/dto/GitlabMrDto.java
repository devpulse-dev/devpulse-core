package ru.x5.devpulse.adapter.reviews.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/** Merge request GitLab (список {@code /merge_requests}). */
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
        GitlabUserDto author
) {}
