package ru.x5.markable.dev.analytics.adapter.kaiten;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация Kaiten HTTP-клиента и rate-limiter'а.
 *
 * <p>Дефолты подобраны под наблюдаемый sliding-window лимит Kaiten (~15-30 RPS):
 * 250 мс между запросами = 4 RPS, 10 retry'ев и до 60 с backoff покрывают
 * восстановление лимита после burst'а.</p>
 */
@ConfigurationProperties(prefix = "kaiten.api")
public record KaitenProperties(
        String url,
        String token,
        long requestDelayMs,
        int maxRetries,
        long retryInitialBackoffMs,
        long retryMaxBackoffMs,
        int pageSize
) {
    public KaitenProperties {
        if (requestDelayMs <= 0) requestDelayMs = 250;
        if (maxRetries <= 0) maxRetries = 10;
        if (retryInitialBackoffMs <= 0) retryInitialBackoffMs = 5_000;
        if (retryMaxBackoffMs <= 0) retryMaxBackoffMs = 60_000;
        if (pageSize <= 0) pageSize = 100;
    }
}
