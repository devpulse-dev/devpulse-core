package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для еженедельной сводки по автору.
 * 
 * <p>Содержит агрегированную статистику по коммитам и изменениям в коде для конкретного автора
 * за неделю. Используется для отображения еженедельных отчётов.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorWeeklySummaryDto {
    
    /**
     * Email автора.
     * 
     * <p>Идентификатор автора в системе.</p>
     */
    private String email;
    
    /**
     * Количество коммитов.
     * 
     * <p>Общее количество коммитов, сделанных автором за неделю.</p>
     */
    private long commits;
    
    /**
     * Количество merge-коммитов.
     * 
     * <p>Количество merge-коммитов, сделанных автором за неделю.</p>
     */
    private long mergeCommits;
    
    /**
     * Количество добавленных строк.
     * 
     * <p>Общее количество строк кода, добавленных автором за неделю.</p>
     */
    private long addedLines;
    
    /**
     * Количество удалённых строк.
     * 
     * <p>Общее количество строк кода, удалённых автором за неделю.</p>
     */
    private long deletedLines;
    
    /**
     * Количество добавленных строк в тестовых файлах.
     * 
     * <p>Количество строк кода, добавленных автором в тестовые файлы за неделю.</p>
     */
    private long testAddedLines;
}
