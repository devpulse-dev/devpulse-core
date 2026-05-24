package ru.x5.markable.dev.analytics.adapter.kaiten.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Пользователь, как его отдаёт Kaiten API.
 * Сериализуем только нужные поля; всё остальное игнорируем.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KaitenUserDto(
        long id,
        String username,
        String email,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("avatar_uploaded_url") String avatarUrl
) {
}
