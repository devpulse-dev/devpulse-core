package ru.x5.markable.dev.analytics.kaiten.config;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "kaiten.api")
public class KaitenProperties {
    private String url = "https://kaiten.x5.ru/api/latest";
    private String token;
    private int timeout = 30000;
    private List<Long> spaceIds;
}
