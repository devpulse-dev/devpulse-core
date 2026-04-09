package ru.x5.markable.dev.analytics.kaiten.rest.dto.card;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class KaitenTypeDto {
    private Long id;
    private String name;
    private Integer color;
    private String letter;

    @JsonProperty("company_id")
    private Long companyId;

    private Boolean archived;
}