package ru.x5.markable.dev.analytics.kaiten.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class KaitenUserDto {
    private Long id;
    private String username;
    private String email;
    @JsonProperty("full_name")
    private String fullName;
    @JsonProperty("avatar_initials_url")
    private String avatarInitialsUrl;
}
