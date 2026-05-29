package ru.x5.devpulse.adapter.persistence.kaiten;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "kaiten_card")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KaitenCardEntity {

    @Id
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status")
    private String status;

    @Column(name = "priority")
    private String priority;

    @Column(name = "space_id")
    private Long spaceId;

    @Column(name = "space_name")
    private String spaceName;

    @Column(name = "board_id")
    private Long boardId;

    @Column(name = "board_name")
    private String boardName;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "owner_name")
    private String ownerName;

    @Column(name = "type_id")
    private Long typeId;

    @Column(name = "type_name")
    private String typeName;

    @Column(name = "column_id")
    private Long columnId;

    @Column(name = "column_name")
    private String columnName;

    @Column(name = "lane_id")
    private Long laneId;

    @Column(name = "lane_name")
    private String laneName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "last_moved_at")
    private LocalDateTime lastMovedAt;

    @Column(name = "lane_changed_at")
    private LocalDateTime laneChangedAt;

    @Column(name = "archived")
    private Boolean archived;

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;

    // custom_fields (jsonb) есть в БД, но не маппится — domain эту колонку не использует.
    // Если когда-нибудь понадобится, маппить нужно через @JdbcTypeCode(SqlTypes.JSON), не как String.

    @Column(name = "url")
    private String url;

    @Column(name = "version")
    private Integer version;
}
