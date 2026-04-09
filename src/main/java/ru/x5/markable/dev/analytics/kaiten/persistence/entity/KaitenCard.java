package ru.x5.markable.dev.analytics.kaiten.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "kaiten_card")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KaitenCard {

    @Id
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status")
    private String status;  // Очередь, В работе, Готово

    @Column(name = "priority")
    private String priority;  // Низкий, Средний, Высокий, Крит

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", columnDefinition = "JSONB")
    private Map<String, Object> customFields;

    @Column(name = "url")
    private String url;

    @Column(name = "version")
    private Integer version;
}
