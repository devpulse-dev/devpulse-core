package ru.x5.devpulse.adapter.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Участник проекта из {@code GET /projects/:id/members/all/:uid}. Нужен только
 * {@code access_level} (10 Guest, 20 Reporter, 30 Developer, 40 Maintainer, 50 Owner).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitlabMemberDto(
        Long id,
        @JsonProperty("access_level") Integer accessLevel
) {}
