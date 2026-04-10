package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

/**
 * DTO для еженедельной статистики коммитов.
 * 
 * <p>Содержит агрегированную статистику по коммитам и изменениям в коде за неделю.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyCommitStatsDto {

    /**
     * Номер недели.
     * 
     * <p>Порядковый номер недели в году.</p>
     */
    private int weekNumber;
    
    /**
     * Начало недели.
     * 
     * <p>Дата начала недели (обычно понедельник).</p>
     */
    private LocalDate weekStart;
    
    /**
     * Конец недели.
     * 
     * <p>Дата конца недели (обычно воскресенье).</p>
     */
    private LocalDate weekEnd;
    
    /**
     * Общее количество коммитов.
     * 
     * <p>Общее количество коммитов, сделанных за неделю.</p>
     */
    private long totalCommits;
    
    /**
     * Общее количество merge-коммитов.
     * 
     * <p>Количество merge-коммитов, сделанных за неделю.</p>
     */
    private long totalMergeCommits;
    
    /**
     * Общее количество добавленных строк.
     * 
     * <p>Общее количество строк кода, добавленных за неделю.</p>
     */
    private long totalAddedLines;
    
    /**
     * Общее количество удалённых строк.
     * 
     * <p>Общее количество строк кода, удалённых за неделю.</p>
     */
    private long totalDeletedLines;
    
    /**
     * Общее количество добавленных строк в тестовых файлах.
     * 
     * <p>Количество строк кода, добавленных в тестовые файлы за неделю.</p>
     */
    private long totalTestAddedLines;
    
    /**
     * Количество уникальных авторов.
     * 
     * <p>Количество уникальных авторов, сделавших коммиты за неделю.</p>
     */
    private long uniqueAuthors;
    
    /**
     * Топ авторов.
     * 
     * <p>Карта с топ авторами за неделю, где ключ - email автора, значение - еженедельная сводка по автору.</p>
     */
    private Map<String, AuthorWeeklySummaryDto> topAuthors;
}
