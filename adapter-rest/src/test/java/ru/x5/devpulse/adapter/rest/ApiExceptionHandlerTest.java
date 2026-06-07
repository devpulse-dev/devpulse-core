package ru.x5.devpulse.adapter.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.SocketTimeoutException;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import ru.x5.devpulse.application.port.out.CollectionAlreadyRunningException;
import ru.x5.devpulse.application.port.out.CollectionRunNotCancellableException;
import ru.x5.devpulse.domain.model.collection.CollectionStatus;

/**
 * Тест маппинга exception → HTTP-статус через {@link ApiExceptionHandler}.
 *
 * <p>Используем dummy-controller вместо реального ({@code UsersController}/etc) — это даёт
 * чистую изоляцию: проверяется именно exception handler, без шумов use case'ов и моков.</p>
 */
@WebMvcTest(controllers = ApiExceptionHandlerTest.BoomController.class)
@Import({ApiExceptionHandler.class, ApiExceptionHandlerTest.BoomController.class})
@DisplayName("ApiExceptionHandler — маппинг exception → HTTP status (RFC 7807)")
class ApiExceptionHandlerTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("IllegalArgumentException → 400 + urn:devpulse:problem:bad-request")
    void illegalArgumentBecomes400() throws Exception {
        mvc.perform(get("/api/v2/_boom").param("kind", "illegal"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:devpulse:problem:bad-request"))
                .andExpect(jsonPath("$.title").value("Bad request"));
    }

    @Test
    @DisplayName("CollectionAlreadyRunningException → 409 + urn:devpulse:problem:conflict")
    void collectionRunningBecomes409() throws Exception {
        mvc.perform(get("/api/v2/_boom").param("kind", "running"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:devpulse:problem:conflict"));
    }

    @Test
    @DisplayName("CollectionRunNotCancellableException → 409 + Collection run not cancellable")
    void notCancellableBecomes409() throws Exception {
        mvc.perform(get("/api/v2/_boom").param("kind", "notcancellable"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:devpulse:problem:conflict"))
                .andExpect(jsonPath("$.title").value("Collection run not cancellable"));
    }

    @Test
    @DisplayName("HttpServerErrorException (Kaiten 500) → 502 Bad Gateway")
    void upstream5xxBecomes502() throws Exception {
        mvc.perform(get("/api/v2/_boom").param("kind", "upstream5xx"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.type").value("urn:devpulse:problem:upstream-error"))
                .andExpect(jsonPath("$.title").value("Upstream service error"));
    }

    @Test
    @DisplayName("HttpClientErrorException (Kaiten 401/403/...) → 502 Bad Gateway (мы не пробрасываем 4xx наружу)")
    void upstream4xxBecomes502() throws Exception {
        mvc.perform(get("/api/v2/_boom").param("kind", "upstream4xx"))
                .andExpect(status().isBadGateway());
    }

    @Test
    @DisplayName("ResourceAccessException с SocketTimeoutException в cause → 504 Gateway Timeout")
    void timeoutBecomes504() throws Exception {
        mvc.perform(get("/api/v2/_boom").param("kind", "timeout"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.type").value("urn:devpulse:problem:upstream-timeout"))
                .andExpect(jsonPath("$.title").value("Upstream timeout"));
    }

    @Test
    @DisplayName("ResourceAccessException БЕЗ timeout (connection refused) → 502 Bad Gateway")
    void connectionRefusedBecomes502() throws Exception {
        mvc.perform(get("/api/v2/_boom").param("kind", "refused"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Upstream service unavailable"));
    }

    @Test
    @DisplayName("Прочий unchecked → 500 Internal Server Error")
    void anyOtherBecomes500() throws Exception {
        mvc.perform(get("/api/v2/_boom").param("kind", "other"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("urn:devpulse:problem:internal"));
    }

    /**
     * Тестовый контроллер: бросает указанный тип exception'а. Используется только в этом тесте.
     */
    @RestController
    @RequestMapping("/_boom")
    static class BoomController {
        @GetMapping
        String boom(@RequestParam String kind) {
            Supplier<RuntimeException> ex = switch (kind) {
                case "illegal" -> () -> new IllegalArgumentException("bad");
                case "running" -> CollectionAlreadyRunningException::new;
                case "notcancellable" -> () -> new CollectionRunNotCancellableException(
                        java.util.UUID.randomUUID(), CollectionStatus.SUCCESS);
                case "upstream5xx" -> () -> HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Kaiten 500",
                        new HttpHeaders(), new byte[0], null);
                case "upstream4xx" -> () -> HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED, "Kaiten 401",
                        new HttpHeaders(), new byte[0], null);
                case "timeout" -> () -> new ResourceAccessException(
                        "I/O error on GET", new SocketTimeoutException("Read timed out"));
                case "refused" -> () -> new ResourceAccessException(
                        "I/O error on GET", new java.net.ConnectException("Connection refused"));
                default -> () -> new RuntimeException("kaboom");
            };
            throw ex.get();
        }
    }
}
