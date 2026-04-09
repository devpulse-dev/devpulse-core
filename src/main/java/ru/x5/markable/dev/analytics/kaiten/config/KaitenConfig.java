package ru.x5.markable.dev.analytics.kaiten.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class KaitenConfig {

    @Bean(name = "kaitenRestTemplate")
    public RestTemplate kaitenRestTemplate() {
        return new RestTemplate();
    }
}
