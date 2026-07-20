package ru.x5.devpulse.adapter.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Пользователь GitLab. {@code public_email} есть только в полном объекте ({@code GET /users}),
 * в MR/approvals/notes приходит «упрощённый» вид без email — там полагаемся на {@code username}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitlabUserDto(
        Long id,
        String username,
        String name,
        @JsonProperty("public_email") String publicEmail
) {}
