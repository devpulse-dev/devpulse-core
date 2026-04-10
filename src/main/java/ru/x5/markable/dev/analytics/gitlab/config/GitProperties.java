package ru.x5.markable.dev.analytics.gitlab.config;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Конфигурационные свойства для работы с Git-репозиториями.
 * 
 * <p>Загружает настройки из application.yml с префиксом "git".</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "git")
public class GitProperties {

    /**
     * Список путей к Git-репозиториям для анализа.
     */
    private List<String> repositories;
    
    /**
     * Токен для доступа к GitLab API (опционально).
     */
    private String token;
    
    /**
     * Директория для кэширования клонированных репозиториев.
     */
    private String cacheDirectory;
}