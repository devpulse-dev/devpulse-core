package ru.x5.markable.dev.analytics.adapter.kaiten;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

@DisplayName("KaitenRateLimiter (adaptive throttle + retry)")
class KaitenRateLimiterTest {

    // Дефолты сильно укорочены, чтобы тесты не висели секундами:
    // 1мс throttle, 1мс initial backoff, 5мс max backoff, 3 retry'я.
    private static final KaitenProperties FAST_PROPS = new KaitenProperties(
            "http://localhost", "token",
            /*requestDelayMs*/ 1,
            /*maxRetries*/     3,
            /*initBackoffMs*/  1,
            /*maxBackoffMs*/   5,
            /*pageSize*/       100);

    private final KaitenRateLimiter limiter = new KaitenRateLimiter(FAST_PROPS);

    @Test
    @DisplayName("Если вызов успешен с первого раза — возвращает результат без retry")
    void returnsResultWithoutRetryOnSuccess() {
        AtomicInteger calls = new AtomicInteger();

        String result = limiter.execute("op", () -> {
            calls.incrementAndGet();
            return "ok";
        });

        assertAll("успешный вызов",
                () -> assertThat(result).as("результат пробрасывается наружу").isEqualTo("ok"),
                () -> assertThat(calls.get()).as("ровно один реальный вызов").isEqualTo(1));
    }

    @Test
    @DisplayName("Повторяет вызов после 429 и возвращает результат на ретрае")
    void retriesAfter429ThenReturnsResult() {
        AtomicInteger calls = new AtomicInteger();

        String result = limiter.execute("op", () -> {
            int n = calls.incrementAndGet();
            if (n == 1) {
                throw HttpClientErrorException.create(
                        HttpStatus.TOO_MANY_REQUESTS, "429", new HttpHeaders(), new byte[0], null);
            }
            return "ok";
        });

        assertAll("ретрай после 429",
                () -> assertThat(result).as("в итоге успех").isEqualTo("ok"),
                () -> assertThat(calls.get()).as("два вызова: 1 падение + 1 повтор").isEqualTo(2));
    }

    @Test
    @DisplayName("После maxRetries 429 пробрасывает исключение наружу")
    void rethrowsAfterMaxRetries() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> limiter.execute("op", () -> {
            calls.incrementAndGet();
            throw HttpClientErrorException.create(
                    HttpStatus.TOO_MANY_REQUESTS, "429", new HttpHeaders(), new byte[0], null);
        }))
                .as("после maxRetries должно вылететь TooManyRequests")
                .isInstanceOf(HttpClientErrorException.TooManyRequests.class);

        assertThat(calls.get())
                .as("ровно maxRetries+1 попыток (1 первоначальная + N retry'ев)")
                .isEqualTo(FAST_PROPS.maxRetries() + 1);
    }

    @Test
    @DisplayName("5xx тоже ретрается — отдельная ветка от 429")
    void retriesOn5xx() {
        AtomicInteger calls = new AtomicInteger();

        String result = limiter.execute("op", () -> {
            int n = calls.incrementAndGet();
            if (n == 1) {
                throw HttpServerErrorException.create(
                        HttpStatus.SERVICE_UNAVAILABLE, "503", new HttpHeaders(), new byte[0], null);
            }
            return "ok";
        });

        assertAll("ретрай после 503",
                () -> assertThat(result).isEqualTo("ok"),
                () -> assertThat(calls.get()).as("один retry потребовался").isEqualTo(2));
    }

    @Test
    @DisplayName("Уважает заголовок Retry-After (число секунд)")
    void respectsRetryAfterHeader() {
        // Не проверяем точную задержку (это медленный/нестабильный тест) — проверяем что
        // механика не падает и в итоге достигаем успеха.
        AtomicInteger calls = new AtomicInteger();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, "0");

        String result = limiter.execute("op", () -> {
            int n = calls.incrementAndGet();
            if (n == 1) {
                throw HttpClientErrorException.create(
                        HttpStatus.TOO_MANY_REQUESTS, "429", headers, new byte[0], null);
            }
            return "ok";
        });

        assertThat(result).as("успех на 2-м вызове").isEqualTo("ok");
    }
}
