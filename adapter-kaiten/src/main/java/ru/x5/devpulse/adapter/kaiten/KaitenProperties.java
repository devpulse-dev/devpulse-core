package ru.x5.devpulse.adapter.kaiten;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация Kaiten HTTP-клиента и rate-limiter'а.
 *
 * <p>Дефолты подобраны под наблюдаемый sliding-window лимит Kaiten (~15-30 RPS):
 * 250 мс между запросами = 4 RPS, 10 retry'ев и до 60 с backoff покрывают
 * восстановление лимита после burst'а.</p>
 *
 * @param url           базовый URL API Kaiten (например {@code https://kaiten.x5.ru/api/latest}).
 *                      Используется HTTP-клиентом
 * @param webBaseUrl    базовый URL web-UI Kaiten (например {@code https://kaiten.x5.ru}). Используется
 *                      для построения ссылки на карточку в REST-ответах ({@code KaitenCard.url}).
 *                      Отделён от {@link #url} потому что API endpoint и UI host могут не совпадать
 *                      (разные пути, разные поддомены — реальный пример: API под {@code /api/latest})
 * @param token         access-token; используется HTTP-клиентом в заголовке Authorization
 * @param insecureSsl   если {@code true} — Kaiten HTTP-клиент собирается с trust-all SSL context
 *                      (отключены проверка цепочки и hostname verification). Только для dev
 *                      на машинах без корпоративного CA в truststore. В проде/CI — {@code false}
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
        boolean insecureSsl
) {
    public KaitenProperties {
        if (requestDelayMs <= 0) requestDelayMs = 250;
        if (maxRetries <= 0) maxRetries = 10;
        if (retryInitialBackoffMs <= 0) retryInitialBackoffMs = 5_000;
        if (retryMaxBackoffMs <= 0) retryMaxBackoffMs = 60_000;
        if (pageSize <= 0) pageSize = 100;
    }
}
