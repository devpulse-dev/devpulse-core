package ru.x5.markable.dev.analytics.gitlab.persistence.entity;

/**
 * Статус выполнения анализа репозиториев.
 * 
 * <p>Определяет текущее состояние процесса анализа Git-репозиториев.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
public enum AnalysisStatus {
    /**
     * Анализ выполняется в данный момент.
     */
    RUNNING,
    
    /**
     * Анализ успешно завершен.
     */
    SUCCESS,
    
    /**
     * Анализ завершился с ошибкой.
     */
    FAILED
}
