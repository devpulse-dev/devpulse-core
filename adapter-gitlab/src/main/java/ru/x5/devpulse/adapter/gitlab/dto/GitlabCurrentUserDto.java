package ru.x5.devpulse.adapter.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Текущий пользователь из {@code GET /user} (запрос от имени пользователя его токеном).
 * В отличие от списочного {@code GitlabUserDto} тут приходит собственный {@code email}
 * аутентифицированного пользователя (не {@code public_email}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitlabCurrentUserDto(
        Long id,
        String username,
        String name,
        String email,
        @JsonProperty("avatar_url") String avatarUrl
) {}
