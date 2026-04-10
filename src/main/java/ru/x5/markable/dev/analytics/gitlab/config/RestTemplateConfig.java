package ru.x5.markable.dev.analytics.gitlab.config;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.security.cert.X509Certificate;

/**
 * Конфигурация RestTemplate для выполнения HTTP-запросов.
 * 
 * <p>Отключает проверку SSL-сертификатов для разработки и настраивает
 * таймауты соединения и чтения.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Создаёт и настраивает бин RestTemplate.
     * 
     * @return настроенный экземпляр RestTemplate
     */
    @Bean
    public RestTemplate restTemplate() {
        // Отключаем проверку SSL для разработки
        disableSslVerification();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000);
        factory.setReadTimeout(30000);

        return new RestTemplate(factory);
    }

    /**
     * Отключает проверку SSL-сертификатов.
     * 
     * <p>Используется только для разработки. В продакшене следует использовать
     * валидные SSL-сертификаты.</p>
     */
    private void disableSslVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                        public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                    }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
