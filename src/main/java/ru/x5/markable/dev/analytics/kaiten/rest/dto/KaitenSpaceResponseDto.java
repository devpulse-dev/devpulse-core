package ru.x5.markable.dev.analytics.kaiten.rest.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KaitenSpaceResponseDto {
    private Long id;
    private String title;
    private String description;
}
