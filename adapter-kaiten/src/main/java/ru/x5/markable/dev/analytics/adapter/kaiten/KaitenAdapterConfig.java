package ru.x5.markable.dev.analytics.adapter.kaiten;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Конфигурация adapter-kaiten: создаёт {@link KaitenHttpClient} через
 * Spring 6+ HttpServiceProxyFactory над {@link RestClient}.
 *
 * <p>Bearer-токен и базовый URL подставляются в RestClient через {@link KaitenProperties}.</p>
 */
@Configuration
@EnableConfigurationProperties(KaitenProperties.class)
class KaitenAdapterConfig {

    @Bean
    RestClient kaitenRestClient(KaitenProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.url())
                .defaultHeader("Authorization", "Bearer " + properties.token())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    KaitenHttpClient kaitenHttpClient(RestClient kaitenRestClient) {
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(kaitenRestClient))
                .build()
                .createClient(KaitenHttpClient.class);
    }
}
