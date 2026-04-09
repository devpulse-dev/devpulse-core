package ru.x5.markable.dev.analytics.kaiten.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.card.KaitenBoardDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.card.KaitenColumnDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.card.KaitenLaneDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.card.KaitenMemberDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.card.KaitenOwnerDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.card.KaitenTagDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.card.KaitenTypeDto;

@Data
public class KaitenCardDto {
    private Long id;
    private String uid;

    @JsonProperty("created")
    private LocalDateTime createdAt;

    @JsonProperty("updated")
    private LocalDateTime updatedAt;

    private Boolean archived;
    private String title;

    @JsonProperty("last_moved_at")
    private LocalDateTime lastMovedAt;

    @JsonProperty("lane_changed_at")
    private LocalDateTime laneChangedAt;

    private Map<String, Object> properties;

    // Вложенные объекты
    private KaitenOwnerDto owner;
    private KaitenBoardDto board;
    private KaitenTypeDto type;
    private KaitenColumnDto column;
    private KaitenLaneDto lane;
    private List<KaitenTagDto> tags;

    // Дополнительные поля
    @JsonProperty("board_id")
    private Long boardId;

    @JsonProperty("column_id")
    private Long columnId;

    @JsonProperty("lane_id")
    private Long laneId;

    @JsonProperty("owner_id")
    private Long ownerId;

    @JsonProperty("type_id")
    private Long typeId;

    private Integer state;
    private Integer condition;
    private List<KaitenMemberDto> members;
}