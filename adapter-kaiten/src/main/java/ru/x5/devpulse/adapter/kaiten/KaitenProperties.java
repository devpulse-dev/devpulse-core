package ru.x5.devpulse.adapter.kaiten;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация Kaiten HTTP-клиента и rate-limiter'а.
 *
 * <p>Дефолты подобраны под наблюдаемый sliding-window лимит Kaiten (~15-30 RPS):
 * 250 мс между запросами = 4 RPS, 10 retry'ев и до 60 с backoff покрывают
 * восстановление лимита после burst'а.</p>
 *
 * @param url            базовый URL API Kaiten (например {@code https://kaiten.x5.ru/api/latest})
 * @param webBaseUrl     базовый URL web-UI Kaiten (например {@code https://kaiten.x5.ru}); для
 *                       построения ссылки на карточку в REST-ответах
 * @param token          access-token; в заголовке Authorization
 * @param insecureSsl    если {@code true} — клиент с trust-all SSL context (только для dev)
 * @param connectTimeout таймаут установки TCP-соединения. Дефолт 5с. Защита от hung DNS / unreachable host
 * @param readTimeout    таймаут чтения ответа. Дефолт 30с. Защита от подвисшего upstream
 */
@ConfigurationProperties(prefix = "kaiten.api")
public record KaitenProperties(
        String url,
        String webBaseUrl,
        String token,
        long requestDelayMs,
        int maxRetries,
        long retryInitialBackoffMs,
        long retryMaxBackoffMs,
        int pageSize,
        boolean insecureSsl,
        Duration connectTimeout,
        Duration readTimeout
) {
    public KaitenProperties {
        if (requestDelayMs <= 0) requestDelayMs = 250;
        if (maxRetries <= 0) maxRetries = 10;
        if (retryInitialBackoffMs <= 0) retryInitialBackoffMs = 5_000;
        if (retryMaxBackoffMs <= 0) retryMaxBackoffMs = 60_000;
        if (pageSize <= 0) pageSize = 100;
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(5);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(30);
    }
}
