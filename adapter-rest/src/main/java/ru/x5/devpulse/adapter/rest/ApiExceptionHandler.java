package ru.x5.devpulse.adapter.rest;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.x5.devpulse.application.port.out.CollectionAlreadyRunningException;

/**
 * Глобальный обработчик ошибок REST API. Формат — RFC 7807 ({@link ProblemDetail}).
 * Spring сам выставит media-type {@code application/problem+json}.
 *
 * <p><b>Маппинг кодов:</b></p>
 * <ul>
 *   <li>{@code 400 Bad Request} — невалидный input (domain validation, malformed query).</li>
 *   <li>{@code 409 Conflict} — сбор уже идёт.</li>
 *   <li>{@code 502 Bad Gateway} — upstream-сервис (Kaiten) ответил ошибкой.</li>
 *   <li>{@code 504 Gateway Timeout} — таймаут до upstream (connect/read).</li>
 *   <li>{@code 500 Internal Server Error} — всё остальное (на самом деле, наша ошибка).</li>
 * </ul>
 *
 * <p><b>Почему отдельные коды для upstream:</b> 500 у клиента — сигнал "сломан бэк, поднимай нас".
 * Когда сломан Kaiten — это не наш баг; фронт должен показать "временно недоступно" и
 * предложить retry, а не звать DevPulse-команду. Корректные коды позволяют клиенту корректно
 * различать сценарии (PagerDuty alert vs notification banner).</p>
 */
@RestControllerAdvice
@Slf4j
class ApiExceptionHandler {

    private static final URI BAD_REQUEST = URI.create("urn:devpulse:problem:bad-request");
    private static final URI CONFLICT = URI.create("urn:devpulse:problem:conflict");
    private static final URI BAD_GATEWAY = URI.create("urn:devpulse:problem:upstream-error");
    private static final URI GATEWAY_TIMEOUT = URI.create("urn:devpulse:problem:upstream-timeout");
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
     * <p>409 — это конфликт состояния ресурса (collection runner), а не невалидный input.
     * Фронт должен показать "сбор уже идёт" и дать GET по последнему CollectionRun.</p>
     */
    @ExceptionHandler(CollectionAlreadyRunningException.class)
    ProblemDetail handleAlreadyRunning(CollectionAlreadyRunningException ex) {
        log.warn("409: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(CONFLICT);
        pd.setTitle("Collection already running");
        return pd;
    }

    /**
     * Upstream (Kaiten) ответил с ошибкой — 4xx/5xx. Для клиента это "внешняя зависимость
     * сломана", не "у нас баг". Возвращаем 502 Bad Gateway.
     *
     * <p>Не пробрасываем upstream-message клиенту: там могут быть internal details (URL, токен
     * fragments, stacktraces). Generic сообщение наружу, полный stacktrace в наши логи.</p>
     */
    @ExceptionHandler({HttpClientErrorException.class, HttpServerErrorException.class})
    ProblemDetail handleUpstreamHttpError(Exception ex) {
        log.error("502 upstream HTTP error", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "Внешний сервис (Kaiten) недоступен");
        pd.setType(BAD_GATEWAY);
        pd.setTitle("Upstream service error");
        return pd;
    }

    /**
     * Сетевой сбой до upstream'а: connection refused / timeout / DNS issue.
     *
     * <p>{@link ResourceAccessException} оборачивает реальные I/O-ошибки. Если в cause-цепочке
     * есть {@code SocketTimeoutException} или {@code HttpTimeoutException} — это **504 Gateway
     * Timeout** (мы не дождались). В остальных случаях (connection refused, DNS) — 502.</p>
     */
    @ExceptionHandler(ResourceAccessException.class)
    ProblemDetail handleUpstreamConnection(ResourceAccessException ex) {
        if (isTimeout(ex)) {
            log.error("504 upstream timeout: {}", ex.getMessage());
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT,
                    "Внешний сервис (Kaiten) не ответил вовремя");
            pd.setType(GATEWAY_TIMEOUT);
            pd.setTitle("Upstream timeout");
            return pd;
        }
        log.error("502 upstream connection error", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "Внешний сервис (Kaiten) недоступен");
        pd.setType(BAD_GATEWAY);
        pd.setTitle("Upstream service unavailable");
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

    /**
     * Распознаёт timeout-ошибки по cause-цепочке. {@link SocketTimeoutException} — classic
     * {@code RestTemplate}/JDK socket таймаут. {@link HttpTimeoutException} — современный
     * {@code java.net.http.HttpClient}.
     */
    private static boolean isTimeout(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof SocketTimeoutException) return true;
            if (t instanceof HttpTimeoutException) return true;
            // Имя класса проверяем чтобы не зависеть от подзависимостей IO (например IOException
            // с message "timeout" — некоторые HTTP клиенты так бросают)
            if (t instanceof IOException && t.getMessage() != null
                    && t.getMessage().toLowerCase().contains("timed out")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
