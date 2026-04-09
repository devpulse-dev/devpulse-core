package ru.x5.markable.dev.analytics.kaiten.rest.dto.card;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class KaitenMemberDto {
    private Long id;
    private String uid;

    @JsonProperty("full_name")
    private String fullName;

    private String email;
    private String username;

    @JsonProperty("avatar_initials_url")
    private String avatarInitialsUrl;

    private Integer type;  // тип участия
}