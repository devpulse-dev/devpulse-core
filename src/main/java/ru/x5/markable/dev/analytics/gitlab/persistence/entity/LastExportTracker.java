package ru.x5.markable.dev.analytics.gitlab.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Сущность для отслеживания времени последнего экспорта данных.
 * 
 * <p>Используется для хранения информации о последнем успешном экспорте
 * различных типов данных (например, ежедневной статистики).</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Entity
@Table(name = "last_export_tracker")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LastExportTracker {

    /**
     * Уникальный идентификатор записи.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Тип экспорта (например: "DAILY_STATS").
     */
    @Column(name = "export_type", nullable = false, unique = true)
    private String exportType;

    /**
     * Время последнего экспорта.
     */
    @Column(name = "last_export_time", nullable = false)
    private LocalDateTime lastExportTime;

    /**
     * Статус последнего экспорта (SUCCESS, FAILED).
     */
    @Column(name = "status")
    private String status;

    /**
     * Сообщение об ошибке, если экспорт завершился с ошибкой.
     */
    @Column(name = "error_message")
    private String errorMessage;

    /**
     * Дата и время создания записи.
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Дата и время последнего обновления записи.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}