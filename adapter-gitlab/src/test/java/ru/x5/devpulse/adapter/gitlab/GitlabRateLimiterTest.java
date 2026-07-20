package ru.x5.devpulse.adapter.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@DisplayName("GitlabRateLimiter (reserve-slot throttle + retry 429/5xx/сеть)")
class GitlabRateLimiterTest {

    // Укороченные дефолты, чтобы тесты не висели: 1мс throttle, 1мс backoff, 3 retry'я.
    private static final GitlabProperties FAST_PROPS = new GitlabProperties(
            /*baseUrl*/            "http://localhost",
            /*token*/             "token",
            /*projects*/          List.of(),
            /*emailDomain*/       "x5.ru",
            /*fetchPublicEmails*/ false,
            /*maxBackfillDays*/   0,
            /*concurrency*/       1,
            /*requestDelayMs*/    1,
            /*maxRetries*/        3,
            /*retryBackoffMs*/    1,
            /*pageSize*/          100,
            /*insecureSsl*/       false,
            /*connectTimeout*/    null,
            /*readTimeout*/       null,
            /*projectReviewTimeout*/ null);

    private final GitlabRateLimiter limiter = new GitlabRateLimiter(FAST_PROPS);

    @Test
    @DisplayName("Успех с первого раза — без retry")
    void returnsResultWithoutRetryOnSuccess() {
        AtomicInteger calls = new AtomicInteger();
        String result = limiter.execute("op", () -> {
            calls.incrementAndGet();
            return "ok";
        });
        assertAll(
                () -> assertThat(result).isEqualTo("ok"),
                () -> assertThat(calls.get()).isEqualTo(1));
    }

    @Test
    @DisplayName("Ретрай после 429 → успех на повторе")
    void retriesAfter429() {
        AtomicInteger calls = new AtomicInteger();
        String result = limiter.execute("op", () -> {
            if (calls.incrementAndGet() == 1) {
                throw HttpClientErrorException.create(
                        HttpStatus.TOO_MANY_REQUESTS, "429", new HttpHeaders(), new byte[0], null);
            }
            return "ok";
        });
        assertAll(
                () -> assertThat(result).isEqualTo("ok"),
                () -> assertThat(calls.get()).isEqualTo(2));
    }

    @Test
    @DisplayName("5xx ретрается")
    void retriesOn5xx() {
        AtomicInteger calls = new AtomicInteger();
        String result = limiter.execute("op", () -> {
            if (calls.incrementAndGet() == 1) {
                throw HttpServerErrorException.create(
                        HttpStatus.SERVICE_UNAVAILABLE, "503", new HttpHeaders(), new byte[0], null);
            }
            return "ok";
        });
        assertAll(
                () -> assertThat(result).isEqualTo("ok"),
                () -> assertThat(calls.get()).isEqualTo(2));
    }

    @Test
    @DisplayName("Сетевая ошибка (ResourceAccessException) теперь ретрается — раньше пробрасывалась сразу")
    void retriesOnNetworkError() {
        AtomicInteger calls = new AtomicInteger();
        String result = limiter.execute("op", () -> {
            if (calls.incrementAndGet() == 1) {
                throw new ResourceAccessException("connection reset");
            }
            return "ok";
        });
        assertAll(
                () -> assertThat(result).isEqualTo("ok"),
                () -> assertThat(calls.get()).as("1 падение + 1 повтор").isEqualTo(2));
    }

    @Test
    @DisplayName("SSL-сбой НЕ ретрается (перманентная проблема truststore) — ровно один вызов")
    void doesNotRetrySslFailure() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> limiter.execute("op", () -> {
            calls.incrementAndGet();
            throw new ResourceAccessException("ssl", new SSLHandshakeException("untrusted cert"));
        })).isInstanceOf(ResourceAccessException.class);
        assertThat(calls.get()).as("SSL — без ретрая").isEqualTo(1);
    }

    @Test
    @DisplayName("После maxRetries 429 пробрасывается наружу")
    void rethrowsAfterMaxRetries() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> limiter.execute("op", () -> {
            calls.incrementAndGet();
            throw HttpClientErrorException.create(
                    HttpStatus.TOO_MANY_REQUESTS, "429", new HttpHeaders(), new byte[0], null);
        })).isInstanceOf(HttpClientErrorException.TooManyRequests.class);
        assertThat(calls.get()).as("maxRetries+1 попыток").isEqualTo(FAST_PROPS.maxRetries() + 1);
    }

    @Test
    @DisplayName("retryAfterMillis: delta-seconds и HTTP-date (future/past), мусор и пусто → empty")
    void parsesRetryAfterForms() {
        assertAll("формы Retry-After (RFC 7231)",
                () -> assertThat(GitlabRateLimiter.retryAfterMillis(header("120")))
                        .as("120 секунд → 120000 мс").contains(120_000L),
                () -> assertThat(GitlabRateLimiter.retryAfterMillis(header("0")))
                        .as("0 секунд → 0 мс").contains(0L),
                () -> assertThat(GitlabRateLimiter.retryAfterMillis(
                                header(httpDate(Instant.now().minusSeconds(60)))))
                        .as("HTTP-date в прошлом → 0").contains(0L),
                () -> assertThat(GitlabRateLimiter.retryAfterMillis(
                                header(httpDate(Instant.now().plusSeconds(120)))).orElseThrow())
                        .as("HTTP-date ~через 120с → близко к 120000").isBetween(60_000L, 121_000L),
                () -> assertThat(GitlabRateLimiter.retryAfterMillis(header("garbage")))
                        .as("не число и не дата → empty").isEmpty(),
                () -> assertThat(GitlabRateLimiter.retryAfterMillis(new HttpHeaders()))
                        .as("нет заголовка → empty").isEmpty());
    }

    private static HttpHeaders header(String retryAfter) {
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.RETRY_AFTER, retryAfter);
        return h;
    }

    private static String httpDate(Instant instant) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(instant.atOffset(ZoneOffset.UTC));
    }
}
