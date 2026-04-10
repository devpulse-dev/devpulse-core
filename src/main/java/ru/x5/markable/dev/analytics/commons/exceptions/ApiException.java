package ru.x5.markable.dev.analytics.commons.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Базовый класс для всех API исключений.
 * 
 * <p>Абстрактный класс, который должен быть расширен конкретными типами исключений API.
 * Предоставляет структуру для обработки ошибок в API с соответствующими HTTP статусами.</p>
 * 
 * <p><strong>Важно:</strong> Это исключение должно выбрасываться только из интеракторов (слой бизнес-логики),
 * но не из сервисов (слой доступа к данным).</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Getter
public abstract class ApiException extends RuntimeException {

    /**
     * Создает исключение с указанным сообщением.
     * 
     * @param message сообщение об ошибке
     */
    protected ApiException(String message) {
        super(message);
    }

    /**
     * Создает исключение с указанным сообщением и причиной.
     * 
     * @param message сообщение об ошибке
     * @param cause причина исключения
     */
    protected ApiException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Возвращает HTTP статус, соответствующий этому типу исключения.
     * 
     * @return HTTP статус
     */
    public abstract HttpStatus getHttpStatus();
}
