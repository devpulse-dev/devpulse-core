package ru.x5.markable.dev.analytics.gitlab.exception;

/**
 * Исключение, выбрасываемое при ошибке сохранения статистики в базу данных.
 * 
 * <p>Используется для обработки ошибок, возникающих при попытке сохранить
 * собранную статистику коммитов в базу данных.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
public class StatisticsPersistenceException extends AnalysisException {

    /**
     * Создает исключение с указанием причины ошибки сохранения.
     * 
     * @param cause причина ошибки
     */
    public StatisticsPersistenceException(Throwable cause) {
        super("Failed to persist statistics", cause);
    }
}
