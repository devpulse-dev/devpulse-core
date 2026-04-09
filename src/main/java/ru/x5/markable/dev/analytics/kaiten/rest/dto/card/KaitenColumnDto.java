package ru.x5.markable.dev.analytics.kaiten.rest.dto.card;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class KaitenColumnDto {
    private Long id;
    private String uid;
    private String title;

    @JsonProperty("sort_order")
    private Integer sortOrder;

    @JsonProperty("col_count")
    private Integer colCount;

    private Integer type;

    @JsonProperty("board_id")
    private Long boardId;
}