package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO для статистики автора.
 * 
 * <p>Содержит агрегированную статистику по коммитам и изменениям в коде для конкретного автора.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
@Builder
public class AuthorStatsDto {

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
    private Long commits;
    
    /**
     * Количество добавленных строк.
     * 
     * <p>Общее количество строк кода, добавленных автором.</p>
     */
    private Long addedLines;
    
    /**
     * Количество удалённых строк.
     * 
     * <p>Общее количество строк кода, удалённых автором.</p>
     */
    private Long deletedLines;
}
