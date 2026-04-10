package ru.x5.markable.dev.analytics.kaiten.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Сущность для хранения карточек из системы Kaiten.
 * 
 * <p>Содержит полную информацию о карточке, включая заголовок, описание, статус,
 * приоритет, владельца, пространство, доску, колонку, дорожку, теги и другие атрибуты.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Entity
@Table(name = "kaiten_card")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KaitenCard {

    /**
     * Уникальный идентификатор карточки в Kaiten.
     */
    @Id
    private Long id;

    /**
     * Заголовок карточки.
     */
    @Column(name = "title", nullable = false)
    private String title;

    /**
     * Описание карточки.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Статус карточки (например: Очередь, В работе, Готово).
     */
    @Column(name = "status")
    private String status;

    /**
     * Приоритет карточки (например: Низкий, Средний, Высокий, Крит).
     */
    @Column(name = "priority")
    private String priority;

    /**
     * Идентификатор пространства.
     */
    @Column(name = "space_id")
    private Long spaceId;

    /**
     * Название пространства.
     */
    @Column(name = "space_name")
    private String spaceName;

    /**
     * Идентификатор доски.
     */
    @Column(name = "board_id")
    private Long boardId;

    /**
     * Название доски.
     */
    @Column(name = "board_name")
    private String boardName;

    /**
     * Идентификатор владельца карточки.
     */
    @Column(name = "owner_id")
    private Long ownerId;

    /**
     * Имя владельца карточки.
     */
    @Column(name = "owner_name")
    private String ownerName;

    /**
     * Идентификатор типа карточки.
     */
    @Column(name = "type_id")
    private Long typeId;

    /**
     * Название типа карточки.
     */
    @Column(name = "type_name")
    private String typeName;

    /**
     * Идентификатор колонки.
     */
    @Column(name = "column_id")
    private Long columnId;

    /**
     * Название колонки.
     */
    @Column(name = "column_name")
    private String columnName;

    /**
     * Идентификатор дорожки (lane).
     */
    @Column(name = "lane_id")
    private Long laneId;

    /**
     * Название дорожки.
     */
    @Column(name = "lane_name")
    private String laneName;

    /**
     * Дата и время создания карточки.
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Дата и время последнего обновления карточки.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Дата и время закрытия карточки.
     */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /**
     * Дата и время последнего перемещения карточки.
     */
    @Column(name = "last_moved_at")
    private LocalDateTime lastMovedAt;

    /**
     * Дата и время последнего изменения дорожки.
     */
    @Column(name = "lane_changed_at")
    private LocalDateTime laneChangedAt;

    /**
     * Признак архивации карточки.
     */
    @Column(name = "archived")
    private Boolean archived;

    /**
     * Теги карточки.
     */
    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;

    /**
     * Кастомные поля карточки в формате JSON.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", columnDefinition = "JSONB")
    private Map<String, Object> customFields;

    /**
     * URL карточки в Kaiten.
     */
    @Column(name = "url")
    private String url;

    /**
     * Версия карточки.
     */
    @Column(name = "version")
    private Integer version;
}
