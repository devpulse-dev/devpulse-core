package ru.x5.devpulse.adapter.rest;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Глобальный обработчик ошибок REST API.
 *
 * <p>Формат — RFC 7807 ({@link ProblemDetail}). Spring сам выставит media-type
 * {@code application/problem+json}.</p>
 */
@RestControllerAdvice
@Slf4j
class ApiExceptionHandler {

    private static final URI BAD_REQUEST = URI.create("urn:markable:problem:bad-request");
    private static final URI INTERNAL = URI.create("urn:markable:problem:internal");

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadDomainInput(IllegalArgumentException ex) {
        log.warn("400: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(BAD_REQUEST);
        pd.setTitle("Bad request");
        return pd;
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class
    })
    ProblemDetail handleMalformedRequest(Exception ex) {
        log.warn("400 (malformed): {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(BAD_REQUEST);
        pd.setTitle("Malformed request");
        return pd;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleAny(Exception ex) {
        log.error("500", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера");
        pd.setType(INTERNAL);
        pd.setTitle("Internal server error");
        return pd;
    }
}
