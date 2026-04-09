package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UnifiedUserDto {
    private Long id;
    private String email;
    private String username;
    private String name;
    private String avatarUrl;
    private Long kaitenId;
    private Integer gitlabId;
}
