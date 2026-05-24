package ru.x5.markable.dev.analytics.adapter.kaiten;

import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Адаптивный rate-limiter для Kaiten API.
 *
 * <p>Логика:
 * <ul>
 *   <li><b>Throttle</b>: выдерживает минимум {@code requestDelayMs} между запросами
 *       (даёт стабильный RPS).</li>
 *   <li><b>Global pause</b>: при получении 429/5xx взводит {@code pauseUntil},
 *       и все потоки ждут истечения паузы перед следующим запросом.</li>
 *   <li><b>Retry с exponential backoff</b>: до {@code maxRetries} попыток с удвоением
 *       backoff'а, до {@code retryMaxBackoffMs}. Учитывает заголовок {@code Retry-After}.</li>
 * </ul>
 *
 * <p>Stateful — один общий бин на приложение. Synchronization через {@code synchronized}
 * блок в {@link #throttle()} — для virtual threads это безопасно (они паркуются на блокировках,
 * не блокируя platform thread).</p>
 */
@Component
@Log4j2
@RequiredArgsConstructor
class KaitenRateLimiter {

    private final KaitenProperties properties;

    private volatile long lastRequestAt = 0L;
    private volatile long pauseUntil = 0L;

    /**
     * Выполняет HTTP-операцию через rate-limiter.
     *
     * @param description короткое описание для логов (например URL)
     * @param call        собственно вызов клиента
     * @return результат вызова
     */
    public <T> T execute(String description, Supplier<T> call) {
        int attempt = 0;
        long backoff = properties.retryInitialBackoffMs();

        while (true) {
            throttle();
            try {
                return call.get();
            } catch (HttpClientErrorException.TooManyRequests e) {
                if (attempt >= properties.maxRetries()) {
                    log.error("Kaiten 429 после {} попыток для {}", attempt, description);
                    throw e;
                }
                long wait = retryAfterMillis(e.getResponseHeaders())
                        .orElse(Math.min(backoff, properties.retryMaxBackoffMs()));
                pauseUntil = System.currentTimeMillis() + wait;
                log.warn("Kaiten 429, пауза {} мс (попытка {}/{}) для {}",
                        wait, attempt + 1, properties.maxRetries(), description);
                sleepQuietly(wait);
                backoff = Math.min(backoff * 2, properties.retryMaxBackoffMs());
                attempt++;
            } catch (HttpServerErrorException | ResourceAccessException e) {
                // SSL handshake fail / unknown certificate — это перманентная ошибка конфигурации,
                // ретраить бессмысленно (просто проедим 5–10 минут на exp backoff). Сразу пробрасываем.
                if (e instanceof ResourceAccessException && isSslFailure(e)) {
                    log.error("Kaiten SSL ошибка для {}: {}. Ретрай отключён — это проблема "
                            + "конфигурации truststore.", description, e.getMessage());
                    throw e;
                }
                if (attempt >= properties.maxRetries()) {
                    log.error("Kaiten {} после {} попыток для {}", e.getClass().getSimpleName(),
                            attempt, description);
                    throw e;
                }
                long wait = Math.min(backoff, properties.retryMaxBackoffMs());
                pauseUntil = System.currentTimeMillis() + wait;
                log.warn("Kaiten {} ({}), пауза {} мс (попытка {}/{}) для {}",
                        e.getClass().getSimpleName(), e.getMessage(),
                        wait, attempt + 1, properties.maxRetries(), description);
                sleepQuietly(wait);
                backoff = Math.min(backoff * 2, properties.retryMaxBackoffMs());
                attempt++;
            }
        }
    }

    /**
     * Глобальный throttle: ждёт окончания global pause + выдерживает requestDelayMs.
     */
    synchronized void throttle() {
        long now = System.currentTimeMillis();
        long pauseWait = pauseUntil - now;
        if (pauseWait > 0) {
            sleepQuietly(pauseWait);
            now = System.currentTimeMillis();
        }
        long delay = properties.requestDelayMs();
        if (delay > 0) {
            long wait = lastRequestAt + delay - now;
            if (wait > 0) {
                sleepQuietly(wait);
            }
        }
        lastRequestAt = System.currentTimeMillis();
    }

    /**
     * Проверяет, является ли ошибка SSL handshake-сбоем (любого вида: untrusted cert,
     * hostname mismatch, и т.п.). Такие ошибки перманентны до правки truststore — ретрай не поможет.
     */
    private static boolean isSslFailure(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof javax.net.ssl.SSLException) return true;
            if (t instanceof java.security.cert.CertificateException) return true;
            t = t.getCause();
        }
        return false;
    }

    /** Парсит заголовок {@code Retry-After} (только число в секундах, без HTTP-date). */
    private static Optional<Long> retryAfterMillis(HttpHeaders headers) {
        if (headers == null) return Optional.empty();
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            long seconds = Long.parseLong(value.trim());
            return Optional.of(seconds * 1000L);
        } catch (NumberFormatException ignore) {
            return Optional.empty();
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
