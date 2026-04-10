package ru.x5.markable.dev.analytics.gitlab.rest.controller;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.CustomErrorResponse;

/**
 * Глобальный обработчик исключений для REST API.
 * 
 * <p>Перехватывает и обрабатывает исключения, возникающие в контроллерах,
 * преобразуя их в соответствующие HTTP-ответы с понятными сообщениями об ошибках.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Обрабатывает исключения валидации аргументов методов.
     * 
     * <p>Перехватывает ошибки валидации полей в запросах и возвращает
     * карту с именами полей и сообщениями об ошибках.</p>
     * 
     * @param ex исключение валидации
     * @return ответ с картой ошибок и статусом BAD_REQUEST
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.error(ex.getMessage(), ex);
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    /**
     * Обрабатывает все необработанные исключения.
     * 
     * <p>Перехватывает любые исключения, которые не были обработаны
     * другими методами, и возвращает общее сообщение об ошибке сервера.</p>
     * 
     * @param ex исключение
     * @return ответ с сообщением об ошибке и статусом INTERNAL_SERVER_ERROR
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CustomErrorResponse> handleGeneralException(Exception ex) {
        log.error(ex.getMessage(), ex);
        return new ResponseEntity<>(
                new CustomErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Произошла ошибка на сервере."),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
