package ru.x5.markable.dev.analytics.gitlab.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Сущность для хранения информации о запуске анализа репозиториев.
 * 
 * <p>Каждая запись представляет собой отдельный запуск анализа с указанием
 * периода, времени начала и окончания, статуса выполнения и возможной ошибки.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Entity
@Table(name = "analysis_run")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisRun {

    /**
     * Уникальный идентификатор запуска анализа.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Дата и время начала анализа.
     */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /**
     * Дата и время окончания анализа.
     */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /**
     * Начальная дата периода анализа.
     */
    @Column(name = "since_date", nullable = false)
    private LocalDate sinceDate;

    /**
     * Конечная дата периода анализа.
     */
    @Column(name = "until_date", nullable = false)
    private LocalDate untilDate;

    /**
     * Статус выполнения анализа.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status;

    /**
     * Сообщение об ошибке, если анализ завершился с ошибкой.
     */
    @Column(name = "error_message")
    private String errorMessage;
}
