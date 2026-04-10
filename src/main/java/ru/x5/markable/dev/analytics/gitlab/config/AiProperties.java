package ru.x5.markable.dev.analytics.gitlab.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Конфигурационные свойства для работы с AI API.
 * 
 * <p>Загружает настройки из application.yml с префиксом "ai".
 * Используется для генерации AI-сводок на основе статистики пользователей.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
    /**
     * URL корпоративного AI API.
     */
    private String url;
    
    /**
     * API ключ для доступа к AI сервису.
     */
    private String apiKey;
    
    /**
     * Имя модели AI для генерации сводок.
     */
    private String model;
    
    /**
     * Таймаут для запросов к AI API в миллисекундах (по умолчанию 30000 мс = 30 сек).
     */
    private int timeout = 30000;

}
