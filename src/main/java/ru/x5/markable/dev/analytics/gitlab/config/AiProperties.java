package ru.x5.markable.dev.analytics.gitlab.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
    // Корпоративный AI
    private String url;
    private String apiKey;
    private String model;
    private int timeout = 30000;

}
