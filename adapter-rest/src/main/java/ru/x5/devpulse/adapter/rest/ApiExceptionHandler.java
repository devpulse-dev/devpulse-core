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
import ru.x5.devpulse.application.port.out.CollectionAlreadyRunningException;

/**
 * Глобальный обработчик ошибок REST API.
 *
 * <p>Формат — RFC 7807 ({@link ProblemDetail}). Spring сам выставит media-type
 * {@code application/problem+json}.</p>
 */
@RestControllerAdvice
@Slf4j
class ApiExceptionHandler {

    private static final URI BAD_REQUEST = URI.create("urn:devpulse:problem:bad-request");
    private static final URI CONFLICT = URI.create("urn:devpulse:problem:conflict");
    private static final URI INTERNAL = URI.create("urn:devpulse:problem:internal");

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

    /**
     * Сбор уже идёт — параллельный запуск не разрешён.
     *
     * <p>409 — потому что это конфликт состояния ресурса (collection runner) с запросом,
     * а не невалидный input. Фронт должен показать «сбор уже идёт» и дать GET по последнему
     * CollectionRun.</p>
     */
    @ExceptionHandler(CollectionAlreadyRunningException.class)
    ProblemDetail handleAlreadyRunning(CollectionAlreadyRunningException ex) {
        log.warn("409: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(CONFLICT);
        pd.setTitle("Collection already running");
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
