package ru.x5.markable.dev.analytics.kaiten.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Конфигурация для работы с Kaiten API.
 * 
 * <p>Настраивает RestTemplate для выполнения HTTP-запросов к Kaiten API.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Configuration
public class KaitenConfig {

    /**
     * Создает RestTemplate для работы с Kaiten API.
     * 
     * <p>Используется для выполнения HTTP-запросов к Kaiten API.</p>
     * 
     * @return настроенный RestTemplate
     */
    @Bean(name = "kaitenRestTemplate")
    public RestTemplate kaitenRestTemplate() {
        return new RestTemplate();
    }
}
