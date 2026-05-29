package ru.x5.devpulse.adapter.kaiten.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Участник карточки Kaiten. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KaitenMemberDto(
        long id,
        @JsonProperty("full_name") String fullName,
        String email,
        Integer type
) {
}
