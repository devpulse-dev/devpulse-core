package ru.x5.devpulse.adapter.kaiten;

import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Конфигурация adapter-kaiten: создаёт {@link KaitenHttpClient} через
 * Spring 6+ HttpServiceProxyFactory над {@link RestClient}.
 *
 * <p><b>HTTP timeouts:</b> connect/read берутся из {@link KaitenProperties}. Дефолты —
 * 5с/30с. Без них hung upstream (DNS drop, Kaiten под нагрузкой) забил бы пул потоков
 * в {@code /profile}, потому что один зависший запрос держал бы блокирующий поток.</p>
 *
 * <p>Bearer-токен и базовый URL подставляются через {@link KaitenProperties}. Если
 * {@code kaiten.api.insecure-ssl=true} — клиент собирается с trust-all SSL context.</p>
 */
@Configuration
@EnableConfigurationProperties(KaitenProperties.class)
@Slf4j
class KaitenAdapterConfig {

    @Bean
    RestClient kaitenRestClient(KaitenProperties properties) {
        ClientHttpRequestFactory factory = properties.insecureSsl()
                ? insecureSslRequestFactory(properties)
                : secureRequestFactory(properties);

        if (properties.insecureSsl()) {
            log.warn("Kaiten HTTP-клиент собран с ОТКЛЮЧЁННОЙ проверкой SSL "
                    + "(kaiten.api.insecure-ssl=true). Это режим только для dev — "
                    + "в проде/CI должно быть false.");
        }

        return RestClient.builder()
                .baseUrl(properties.url())
                .defaultHeader("Authorization", "Bearer " + properties.token())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(factory)
                .build();
    }

    @Bean
    KaitenHttpClient kaitenHttpClient(RestClient kaitenRestClient) {
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(kaitenRestClient))
                .build()
                .createClient(KaitenHttpClient.class);
    }

    /**
     * Дефолтный SSL trust + явные connect/read timeouts из {@link KaitenProperties}.
     *
     * <p>{@code connectTimeout} — у underlying {@code HttpClient}. {@code readTimeout} —
     * у {@code JdkClientHttpRequestFactory} (применяется на каждый запрос).</p>
     */
    private static ClientHttpRequestFactory secureRequestFactory(KaitenProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    /**
     * Trust-all SSL context + connect/read timeouts. Только для dev.
     */
    private static ClientHttpRequestFactory insecureSslRequestFactory(KaitenProperties properties) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{TRUST_ALL}, new SecureRandom());

            SSLParameters params = new SSLParameters();
            // Пустая строка отключает hostname verification в JDK HttpClient.
            params.setEndpointIdentificationAlgorithm("");

            HttpClient httpClient = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .sslParameters(params)
                    .connectTimeout(properties.connectTimeout())
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(properties.readTimeout());
            return factory;
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось собрать insecure SSL context для Kaiten", e);
        }
    }

    private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { /* trust all */ }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { /* trust all */ }
    };
}
