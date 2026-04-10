package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO для ответа с результатами анализа репозиториев.
 * 
 * <p>Содержит статистику по коммитам и изменениям в коде для указанного пользователя.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
@Builder
public class AnalysisResponse {

    /**
     * Email пользователя, для которого выполнен анализ.
     */
    private String email;
    
    /**
     * Количество merge-коммитов.
     */
    long mergeCommits;
    
    /**
     * Общее количество коммитов.
     */
    long commits;
    
    /**
     * Количество добавленных строк кода.
     */
    long added;
    
    /**
     * Количество удалённых строк кода.
     */
    long deleted;
    
    /**
     * Количество добавленных строк в тестовых файлах.
     */
    long testAdded;
}