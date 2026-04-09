package ru.x5.markable.dev.analytics.kaiten.rest.dto.card;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class KaitenOwnerDto {
    private Long id;
    private String uid;

    @JsonProperty("full_name")
    private String fullName;

    private String email;
    private String username;

    @JsonProperty("avatar_initials_url")
    private String avatarInitialsUrl;

    @JsonProperty("avatar_uploaded_url")
    private String avatarUploadedUrl;

    private String initials;

    @JsonProperty("avatar_type")
    private Integer avatarType;

    private String lng;
    private String timezone;
    private String theme;
    private LocalDateTime created;
    private LocalDateTime updated;
    private Boolean activated;

    @JsonProperty("ui_version")
    private Integer uiVersion;

    private Boolean virtual;
}