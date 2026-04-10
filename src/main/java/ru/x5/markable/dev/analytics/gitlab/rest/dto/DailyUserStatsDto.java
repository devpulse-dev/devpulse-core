package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для ежедневной статистики пользователя.
 * 
 * <p>Содержит статистику по коммитам и изменениям в коде для конкретного пользователя за конкретный день.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyUserStatsDto {
    
    /**
     * Дата статистики.
     * 
     * <p>День, за который собрана статистика.</p>
     */
    private LocalDate date;
    
    /**
     * Email пользователя.
     * 
     * <p>Идентификатор пользователя в системе.</p>
     */
    private String email;
    
    /**
     * Количество коммитов.
     * 
     * <p>Количество коммитов, сделанных пользователем за день.</p>
     */
    private long commits;
    
    /**
     * Количество merge-коммитов.
     * 
     * <p>Количество merge-коммитов, сделанных пользователем за день.</p>
     */
    private long mergeCommits;
    
    /**
     * Количество добавленных строк.
     * 
     * <p>Количество строк кода, добавленных пользователем за день.</p>
     */
    private long addedLines;
    
    /**
     * Количество удалённых строк.
     * 
     * <p>Количество строк кода, удалённых пользователем за день.</p>
     */
    private long deletedLines;
    
    /**
     * Количество добавленных строк в тестовых файлах.
     * 
     * <p>Количество строк кода, добавленных пользователем в тестовые файлы за день.</p>
     */
    private long testAddedLines;
}
