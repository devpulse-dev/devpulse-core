package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * DTO для структурированного ответа об ошибке в формате JSON.
 * 
 * <p>Используется для структурированной обработки ошибок в приложении и предоставляет
 * клиенту информацию о статусе HTTP и сообщении об ошибке.</p>
 * 
 * <p>Этот класс используется глобальным обработчиком исключений для возврата
 * унифицированных ответов об ошибках клиенту.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see ru.x5.markable.dev.analytics.gitlab.rest.controller.GlobalExceptionHandler
 */
@Data
public class CustomErrorResponse {

    /**
     * Код состояния HTTP.
     * 
     * <p>Код состояния HTTP, связанный с ошибкой.</p>
     */
    private final HttpStatusCode status;

    /**
     * Сообщение об ошибке.
     * 
     * <p>Сообщение об ошибке, которое будет возвращено клиенту.</p>
     */
    private final String message;

    /**
     * Конструктор для создания объекта CustomErrorResponse.
     *
     * @param status Код состояния HTTP, связанный с ошибкой
     * @param message Сообщение об ошибке, которое будет возвращено клиенту
     */
    public CustomErrorResponse(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
