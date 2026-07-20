package ru.x5.devpulse.adapter.gitlab;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
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
 * Адаптивный rate-limiter для GitLab API — общий транспортный компонент {@code adapter-gitlab}
 * (используется и сбором ревью, и identity-резолвом).
 *
 * <p>Устроен по образцу {@code KaitenRateLimiter} (единый рабочий паттерн в проекте):
 * <ul>
 *   <li><b>Throttle (lock-free reserve-slot)</b>: держит минимум {@code requestDelayMs} между
 *       запросами <b>глобально</b> через {@link #nextSlotAt}, а не per-thread. Это критично:
 *       ревью собираются fan-out'ом на {@code concurrency} virtual threads — при старом
 *       {@code Thread.sleep} каждый поток спал сам по себе и общего потолка RPS не было
 *       (реальный burst = {@code concurrency} одновременных запросов).</li>
 *   <li><b>Global pause</b>: на 429/5xx взводит {@link #pauseUntil} через
 *       {@code updateAndGet(Math::max)} — более длинная пауза доминирует над короткой при гонке.</li>
 *   <li><b>Retry</b>: до {@code maxRetries} с экспоненциальным backoff + jitter. Учитывает
 *       {@code Retry-After} (delta-seconds и HTTP-date). Ретраятся 429, 5xx и <b>сетевые</b>
 *       ошибки (read/connect timeout, RST) — раньше сетевые не ретраились вовсе. SSL-сбой
 *       перманентен (проблема truststore) — без ретрая.</li>
 * </ul>
 *
 * <p>Lock-free: всё состояние — через {@link AtomicLong#updateAndGet}, sleep'ы вне локов, поэтому
 * virtual threads не сериализуются на мониторе.</p>
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class GitlabRateLimiter {

    /** Потолок экспоненциального backoff (мс). */
    private static final long RETRY_MAX_BACKOFF_MS = 60_000;

    private final GitlabProperties properties;

    /**
     * Время следующего разрешённого запроса (epoch millis). Reserve-slot: каждый поток через
     * {@code updateAndGet} двигает его на свой слот в будущем и спит до него. Два параллельных
     * потока получают разные слоты — общий RPS выдержан.
     */
    private final AtomicLong nextSlotAt = new AtomicLong(0L);

    /** Глобальная пауза после 429/5xx; {@code updateAndGet(Math::max)} — длинная пауза доминирует. */
    private final AtomicLong pauseUntil = new AtomicLong(0L);

    public <T> T execute(String description, Supplier<T> call) {
        int attempt = 0;

        while (true) {
            throttle();
            try {
                return call.get();
            } catch (HttpClientErrorException.TooManyRequests e) {
                if (attempt >= properties.maxRetries()) {
                    log.error("GitLab 429 после {} попыток для {}", attempt, description);
                    throw e;
                }
                long fallback = jitter(Math.min(backoffAt(attempt), RETRY_MAX_BACKOFF_MS));
                long wait = retryAfterMillis(e.getResponseHeaders()).orElse(fallback);
                setGlobalPause(wait);
                log.warn("GitLab 429, пауза {} мс (попытка {}/{}) для {}",
                        wait, attempt + 1, properties.maxRetries(), description);
                sleepQuietly(wait);
                attempt++;
            } catch (HttpServerErrorException | ResourceAccessException e) {
                // SSL handshake fail / untrusted cert — перманентная ошибка конфигурации truststore,
                // ретраить бессмысленно.
                if (e instanceof ResourceAccessException && isSslFailure(e)) {
                    log.error("GitLab SSL ошибка для {}: {}. Ретрай отключён — это проблема "
                            + "конфигурации truststore.", description, e.getMessage());
                    throw e;
                }
                if (attempt >= properties.maxRetries()) {
                    log.error("GitLab {} после {} попыток для {}", e.getClass().getSimpleName(),
                            attempt, description);
                    throw e;
                }
                long wait = jitter(Math.min(backoffAt(attempt), RETRY_MAX_BACKOFF_MS));
                setGlobalPause(wait);
                log.warn("GitLab {} ({}), пауза {} мс (попытка {}/{}) для {}",
                        e.getClass().getSimpleName(), e.getMessage(),
                        wait, attempt + 1, properties.maxRetries(), description);
                sleepQuietly(wait);
                attempt++;
            }
        }
    }

    /** Экспоненциальный backoff для попытки {@code attempt} (0-based): base × 2^attempt. */
    private long backoffAt(int attempt) {
        long base = properties.retryBackoffMs();
        // clamp shift, чтобы не переполнить long на большом числе ретраев
        int shift = Math.min(attempt, 20);
        return base << shift;
    }

    /**
     * Throttle: сначала дождаться глобальной паузы, затем зарезервировать слот в будущем
     * (reserve-slot pattern, lock-free). Sleep — вне локов.
     */
    void throttle() {
        long pauseLeft = pauseUntil.get() - System.currentTimeMillis();
        if (pauseLeft > 0) {
            sleepQuietly(pauseLeft);
        }
        long delay = Math.max(0, properties.requestDelayMs());
        long mySlot = nextSlotAt.updateAndGet(prev -> Math.max(System.currentTimeMillis(), prev + delay));
        long sleepFor = mySlot - System.currentTimeMillis();
        if (sleepFor > 0) {
            sleepQuietly(sleepFor);
        }
    }

    /**
     * Equal jitter: половина backoff фиксирована + случайная половина. Размывает синхронный
     * ретрай множества потоков, получивших 429 одновременно (thundering herd на восстановлении лимита).
     */
    private static long jitter(long backoffMs) {
        if (backoffMs <= 0) return 0;
        long half = backoffMs / 2;
        return half + ThreadLocalRandom.current().nextLong(half + 1);
    }

    private void setGlobalPause(long waitMs) {
        long target = System.currentTimeMillis() + waitMs;
        pauseUntil.updateAndGet(prev -> Math.max(prev, target));
    }

    private static boolean isSslFailure(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof javax.net.ssl.SSLException) return true;
            if (t instanceof java.security.cert.CertificateException) return true;
            t = t.getCause();
        }
        return false;
    }

    /**
     * Парсит {@code Retry-After} (RFC 7231): delta-seconds ({@code "120"}) или HTTP-date (RFC 1123).
     * Дата в прошлом → 0. Не распарсилось → {@link Optional#empty()} (вызывающий уйдёт на backoff).
     * Package-private static — для прямого юнит-теста парсинга.
     */
    static Optional<Long> retryAfterMillis(HttpHeaders headers) {
        if (headers == null) return Optional.empty();
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) return Optional.empty();
        String trimmed = value.trim();

        try {
            long seconds = Long.parseLong(trimmed);
            return Optional.of(Math.max(0L, seconds * 1000L));
        } catch (NumberFormatException notSeconds) {
            // не число — пробуем HTTP-date ниже
        }
        try {
            ZonedDateTime when = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
            long millis = Duration.between(Instant.now(), when.toInstant()).toMillis();
            return Optional.of(Math.max(0L, millis));
        } catch (DateTimeParseException notDate) {
            log.debug("GitLab: Retry-After '{}' — ни delta-seconds, ни HTTP-date, игнорируем", trimmed);
            return Optional.empty();
        }
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Прерывание = отмена сбора (fan-out shutdownNow / стоп прогона) → выходим из retry-цикла.
            throw new IllegalStateException("GitLab-запрос прерван", e);
        }
    }
}
