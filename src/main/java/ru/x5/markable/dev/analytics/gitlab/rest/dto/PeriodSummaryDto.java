package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import java.time.LocalDate;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для сводки за период.
 * 
 * <p>Содержит агрегированную статистику по коммитам и изменениям в коде за указанный период.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodSummaryDto {
    
    /**
     * Общее количество коммитов.
     * 
     * <p>Общее количество коммитов, сделанных за период.</p>
     */
    private long totalCommits;
    
    /**
     * Общее количество merge-коммитов.
     * 
     * <p>Количество merge-коммитов, сделанных за период.</p>
     */
    private long totalMergeCommits;
    
    /**
     * Общее количество добавленных строк.
     * 
     * <p>Общее количество строк кода, добавленных за период.</p>
     */
    private long totalAddedLines;
    
    /**
     * Общее количество удалённых строк.
     * 
     * <p>Общее количество строк кода, удалённых за период.</p>
     */
    private long totalDeletedLines;
    
    /**
     * Общее количество добавленных строк в тестовых файлах.
     * 
     * <p>Количество строк кода, добавленных в тестовые файлы за период.</p>
     */
    private long totalTestAddedLines;
    
    /**
     * Количество уникальных авторов.
     * 
     * <p>Количество уникальных авторов, сделавших коммиты за период.</p>
     */
    private long uniqueAuthors;
    
    /**
     * Начальная дата периода.
     * 
     * <p>Минимальная дата в данных за период.</p>
     */
    private LocalDate dateFrom;
    
    /**
     * Конечная дата периода.
     * 
     * <p>Максимальная дата в данных за период.</p>
     */
    private LocalDate dateTo;
    
    /**
     * Топ авторов.
     * 
     * <p>Карта с топ авторами за период, где ключ - email автора, значение - сводка по автору.</p>
     */
    private Map<String, AuthorSummaryDto> topAuthors;
}
