package ru.x5.devpulse.adapter.reviews;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

/**
 * Простой троттлинг + retry для GitLab API.
 *
 * <p>GitLab-лимиты щедрее Kaiten (обычно ~600 req/min на аутентифицированного),
 * поэтому без адаптивной паузы: фиксированная задержка {@code requestDelayMs}
 * между запросами + exp-backoff retry на 429/5xx.</p>
 *
 * <p>Однопоточный сбор (одна collection-фаза), поэтому без lock-free reserve-slot —
 * хватает простого {@code sleep} между вызовами.</p>
 */
@Component
@Log4j2
@RequiredArgsConstructor
class GitlabRateLimiter {

    private final GitlabProperties properties;

    public <T> T execute(String description, Supplier<T> call) {
        int attempt = 0;
        long backoff = properties.retryBackoffMs();

        while (true) {
            sleep(properties.requestDelayMs());
            try {
                return call.get();
            } catch (HttpClientErrorException.TooManyRequests | HttpServerErrorException e) {
                if (attempt >= properties.maxRetries()) {
                    log.error("GitLab {} после {} попыток для {}",
                            e.getClass().getSimpleName(), attempt, description);
                    throw e;
                }
                long wait = Math.min(backoff, 60_000);
                log.warn("GitLab {} — пауза {} мс (попытка {}/{}) для {}",
                        e.getClass().getSimpleName(), wait, attempt + 1, properties.maxRetries(), description);
                sleep(wait);
                backoff *= 2;
                attempt++;
            }
        }
    }

    private static void sleep(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Сбор ревью прерван", e);
        }
    }
}
