package ru.x5.devpulse.adapter.kaiten;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
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
 *       (стабильный RPS) через atomic reserve-slot pattern.</li>
 *   <li><b>Global pause</b>: при 429/5xx взводит {@link #pauseUntil} через
 *       {@code updateAndGet(Math::max)} — два потока с разными {@code Retry-After}
 *       не перетрут друг другу более длинную паузу более короткой.</li>
 *   <li><b>Retry с exponential backoff</b>: до {@code maxRetries} попыток с удвоением
 *       backoff'а, до {@code retryMaxBackoffMs}. Учитывает заголовок {@code Retry-After}.</li>
 * </ul>
 *
 * <p>Stateful — один общий бин на приложение. Lock-free: все обновления состояния — через
 * {@link AtomicLong#updateAndGet}. Sleep'ы происходят БЕЗ держания каких-либо локов, поэтому
 * параллельные потоки не сериализуются на одном мониторе (важно для virtual threads, которые
 * под нагрузкой иначе создавали бы очередь на single monitor).</p>
 */
@Component
@Log4j2
@RequiredArgsConstructor
class KaitenRateLimiter {

    private final KaitenProperties properties;

    /**
     * Время следующего разрешённого запроса (epoch millis). Reserve-slot pattern:
     * каждый поток через {@code updateAndGet} двигает его на свой слот в будущем
     * и спит до этого слота. Без conflicts: два параллельных потока получают разные слоты.
     */
    private final AtomicLong nextSlotAt = new AtomicLong(0L);

    /**
     * Глобальная пауза после 429/5xx. Обновляется через {@code updateAndGet(Math::max)} —
     * более длинная пауза доминирует над более короткой при гонке.
     */
    private final AtomicLong pauseUntil = new AtomicLong(0L);

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
                setGlobalPause(wait);
                log.warn("Kaiten 429, пауза {} мс (попытка {}/{}) для {}",
                        wait, attempt + 1, properties.maxRetries(), description);
                sleepQuietly(wait);
                backoff = Math.min(backoff * 2, properties.retryMaxBackoffMs());
                attempt++;
            } catch (HttpServerErrorException | ResourceAccessException e) {
                // SSL handshake fail / unknown certificate — перманентная ошибка конфигурации,
                // ретраить бессмысленно. Сразу пробрасываем.
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
                setGlobalPause(wait);
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
     * Глобальный throttle: сначала ждёт окончания paused, потом резервирует слот в будущем
     * с {@code requestDelayMs} от предыдущего занятого слота.
     *
     * <p><b>Reserve-slot pattern (lock-free):</b> {@code updateAndGet} атомарно вычисляет
     * новый слот = max(now, prevSlot + delay). Sleep происходит ПОСЛЕ резервирования и
     * ВНЕ любых locks — параллельные потоки могут резервировать свои слоты дальше по времени
     * пока этот спит.</p>
     */
    void throttle() {
        // 1. Если стоит глобальная пауза — сначала её дождаться (вне любых локов).
        long pauseLeft = pauseUntil.get() - System.currentTimeMillis();
        if (pauseLeft > 0) {
            sleepQuietly(pauseLeft);
        }

        // 2. Зарезервировать слот.
        long delay = Math.max(0, properties.requestDelayMs());
        long mySlot = nextSlotAt.updateAndGet(prev -> {
            long now = System.currentTimeMillis();
            return Math.max(now, prev + delay);
        });

        // 3. Дождаться своего слота (если он в будущем).
        long sleepFor = mySlot - System.currentTimeMillis();
        if (sleepFor > 0) {
            sleepQuietly(sleepFor);
        }
    }

    /**
     * Устанавливает глобальную паузу {@code now + waitMs}, но не уменьшает её если уже стоит
     * более длинная. Защита от ситуации: thread A получил Retry-After=60s, thread B одновременно
     * получил Retry-After=5s — оставляем 60s.
     */
    private void setGlobalPause(long waitMs) {
        long target = System.currentTimeMillis() + waitMs;
        pauseUntil.updateAndGet(prev -> Math.max(prev, target));
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
