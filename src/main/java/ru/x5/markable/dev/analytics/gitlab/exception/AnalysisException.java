package ru.x5.markable.dev.analytics.gitlab.exception;

/**
 * Базовый класс для исключений, возникающих при анализе Git-репозиториев.
 * 
 * <p>Абстрактный класс, который должен быть расширен конкретными типами исключений анализа.
 * Используется для обработки ошибок, возникающих в процессе сбора и анализа статистики коммитов.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
public abstract class AnalysisException extends RuntimeException {

    /**
     * Создает исключение с указанным сообщением.
     * 
     * @param message сообщение об ошибке
     */
    protected AnalysisException(String message) {
        super(message);
    }

    /**
     * Создает исключение с указанным сообщением и причиной.
     * 
     * @param message сообщение об ошибке
     * @param cause причина исключения
     */
    protected AnalysisException(String message, Throwable cause) {
        super(message, cause);
    }

}
