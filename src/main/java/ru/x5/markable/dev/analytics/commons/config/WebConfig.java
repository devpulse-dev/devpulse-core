package ru.x5.markable.dev.analytics.commons.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Конфигурация веб-приложения.
 * 
 * <p>Настраивает CORS (Cross-Origin Resource Sharing) для API endpoints.</p>
 * 
 * <p>Разрешает запросы с фронтенда на порту 9000 к API endpoints на порту 8080.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Настраивает CORS маппинги для API endpoints.
     * 
     * <p>Разрешает запросы с http://localhost:9000 к /api/** endpoints.</p>
     * 
     * @param registry реестр CORS маппингов
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:9000") // Конкретный origin, не "*"
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true) // Разрешено с конкретным origin
                .maxAge(3600);
    }

}
