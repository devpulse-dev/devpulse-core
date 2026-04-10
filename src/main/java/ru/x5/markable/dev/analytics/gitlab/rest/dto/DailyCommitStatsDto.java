package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для ежедневной статистики коммитов.
 * 
 * <p>Содержит агрегированную статистику по коммитам и изменениям в коде за конкретный день.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyCommitStatsDto {
    
    /**
     * Дата статистики.
     * 
     * <p>День, за который собрана статистика.</p>
     */
    private LocalDate date;
    
    /**
     * Общее количество коммитов.
     * 
     * <p>Общее количество коммитов, сделанных за день.</p>
     */
    private long totalCommits;
    
    /**
     * Общее количество merge-коммитов.
     * 
     * <p>Количество merge-коммитов, сделанных за день.</p>
     */
    private long totalMergeCommits;
    
    /**
     * Общее количество добавленных строк.
     * 
     * <p>Общее количество строк кода, добавленных за день.</p>
     */
    private long totalAddedLines;
    
    /**
     * Общее количество удалённых строк.
     * 
     * <p>Общее количество строк кода, удалённых за день.</p>
     */
    private long totalDeletedLines;
    
    /**
     * Общее количество добавленных строк в тестовых файлах.
     * 
     * <p>Количество строк кода, добавленных в тестовые файлы за день.</p>
     */
    private long totalTestAddedLines;
}
