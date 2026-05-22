package ru.x5.markable.dev.analytics.kaiten.config;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Конфигурационные свойства для работы с Kaiten API.
 * 
 * <p>Загружает настройки из application.yml с префиксом "kaiten.api".
 * Используется для интеграции с системой управления задачами Kaiten.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "kaiten.api")
public class KaitenProperties {
    /**
     * URL Kaiten API (по умолчанию https://kaiten.x5.ru/api/latest).
     */
    private String url = "https://kaiten.x5.ru/api/latest";
    
    /**
     * API токен для аутентификации в Kaiten.
     */
    private String token;
    
    /**
     * Таймаут для запросов к Kaiten API в миллисекундах (по умолчанию 30000 мс = 30 сек).
     */
    private int timeout = 30000;
    
    /**
     * Список идентификаторов пространств (spaces) в Kaiten для синхронизации.
     */
    private List<Long> spaceIds;

    /**
     * Минимальная задержка между HTTP-запросами в Kaiten API (мс).
     * 250 ms ≈ 4 RPS — безопасно для sliding-window лимита Kaiten.
     */
    private long requestDelayMs = 250;

    /**
     * Максимальное число повторных попыток после ошибки rate limit (429) или серверных ошибок (5xx).
     */
    private int maxRetries = 10;

    /**
     * Базовая задержка перед повторной попыткой (мс). Используется экспоненциальный backoff,
     * если сервер не прислал заголовок Retry-After.
     */
    private long retryInitialBackoffMs = 5000;

    /**
     * Максимальная задержка перед повторной попыткой (мс).
     * 60 секунд достаточно для восстановления sliding-window лимита.
     */
    private long retryMaxBackoffMs = 60000;
}
