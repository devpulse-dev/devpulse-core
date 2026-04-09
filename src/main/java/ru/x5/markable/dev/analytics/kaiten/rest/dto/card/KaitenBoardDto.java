package ru.x5.markable.dev.analytics.kaiten.rest.dto.card;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class KaitenBoardDto {
    private Long id;
    private String uid;
    private String title;

    @JsonProperty("external_id")
    private String externalId;
}