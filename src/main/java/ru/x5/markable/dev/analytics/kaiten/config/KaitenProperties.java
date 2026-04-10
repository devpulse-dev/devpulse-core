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
}
