package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для сводки по автору.
 * 
 * <p>Содержит агрегированную статистику по коммитам и изменениям в коде для конкретного автора.
 * Используется для отображения сводной информации в отчётах.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorSummaryDto {
    
    /**
     * Email автора.
     * 
     * <p>Идентификатор автора в системе.</p>
     */
    private String email;
    
    /**
     * Количество коммитов.
     * 
     * <p>Общее количество коммитов, сделанных автором.</p>
     */
    private long commits;
    
    /**
     * Количество добавленных строк.
     * 
     * <p>Общее количество строк кода, добавленных автором.</p>
     */
    private long addedLines;
    
    /**
     * Количество удалённых строк.
     * 
     * <p>Общее количество строк кода, удалённых автором.</p>
     */
    private long deletedLines;
}
