package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * DTO для задачи с коммитами.
 * 
 * <p>Содержит информацию о задаче и связанных с ней коммитах.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskWithCommitsDto {
    
    /**
     * Номер задачи.
     * 
     * <p>Уникальный идентификатор задачи.</p>
     */
    private String taskNumber;
    
    /**
     * Заголовок задачи.
     * 
     * <p>Название или заголовок задачи.</p>
     */
    private String taskTitle;
    
    /**
     * Список коммитов.
     * 
     * <p>Список коммитов, связанных с задачей.</p>
     */
    private List<CommitDetailDto> commits;
}
