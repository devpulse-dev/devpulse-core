package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * DTO для сводки по задаче.
 * 
 * <p>Содержит агрегированную информацию о коммитах, связанных с конкретной задачей.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSummaryDto {
    
    /**
     * Номер задачи.
     * 
     * <p>Идентификатор задачи (например, номер карточки в Kaiten).</p>
     */
    private String taskNumber;
    
    /**
     * Количество коммитов.
     * 
     * <p>Количество коммитов, связанных с задачей.</p>
     */
    private long commitCount;
    
    /**
     * Дата первого коммита.
     * 
     * <p>Дата и время первого коммита, связанного с задачей.</p>
     */
    private LocalDateTime firstCommitDate;
    
    /**
     * Дата последнего коммита.
     * 
     * <p>Дата и время последнего коммита, связанного с задачей.</p>
     */
    private LocalDateTime lastCommitDate;
}
