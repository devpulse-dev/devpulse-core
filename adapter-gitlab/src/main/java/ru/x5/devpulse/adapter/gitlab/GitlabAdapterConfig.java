package ru.x5.devpulse.adapter.gitlab;

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
import org.springframework.web.util.DefaultUriBuilderFactory;

/**
 * Конфигурация adapter-reviews: {@link GitlabHttpClient} через HttpServiceProxyFactory над RestClient.
 *
 * <p><b>Аутентификация GitLab:</b> заголовок {@code PRIVATE-TOKEN: <token>} (НЕ Bearer).</p>
 *
 * <p><b>Кодирование URI:</b> {@code EncodingMode.VALUES_ONLY} — значения path-переменных
 * (путь проекта {@code namespace/repo}) кодируются целиком, включая {@code /} → {@code %2F},
 * как требует GitLab для {@code /projects/:id}. Query-параметры тоже кодируются.</p>
 *
 * <p>{@code gitlab.api.insecure-ssl=true} — trust-all (scm.x5.ru за внутренним X5 CA, dev-режим).</p>
 */
@Configuration
@EnableConfigurationProperties({GitlabProperties.class, GitRepoProperties.class})
@Slf4j
class GitlabAdapterConfig {

    @Bean
    RestClient gitlabRestClient(GitlabProperties properties) {
        ClientHttpRequestFactory factory = properties.insecureSsl()
                ? insecureSslRequestFactory(properties)
                : secureRequestFactory(properties);

        if (properties.insecureSsl()) {
            log.warn("GitLab HTTP-клиент собран с ОТКЛЮЧЁННОЙ проверкой SSL "
                    + "(gitlab.api.insecure-ssl=true) — режим только для dev.");
        }

        DefaultUriBuilderFactory uriFactory = new DefaultUriBuilderFactory(
                properties.baseUrl() == null ? "" : properties.baseUrl());
        uriFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);

        return RestClient.builder()
                .uriBuilderFactory(uriFactory)
                .defaultHeader("PRIVATE-TOKEN", properties.token() == null ? "" : properties.token())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(factory)
                .build();
    }

    @Bean
    GitlabHttpClient gitlabHttpClient(RestClient gitlabRestClient) {
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(gitlabRestClient))
                .build()
                .createClient(GitlabHttpClient.class);
    }

    private static ClientHttpRequestFactory secureRequestFactory(GitlabProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    private static ClientHttpRequestFactory insecureSslRequestFactory(GitlabProperties properties) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{TRUST_ALL}, new SecureRandom());

            SSLParameters params = new SSLParameters();
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
            throw new IllegalStateException("Не удалось собрать insecure SSL context для GitLab", e);
        }
    }

    private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { /* trust all */ }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { /* trust all */ }
    };
}
