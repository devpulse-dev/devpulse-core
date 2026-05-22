package ru.x5.markable.dev.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Composition root.
 *
 * <p>Лежит в корневом пакете {@code ru.x5.markable.dev.analytics} — это позволяет
 * Spring Boot и Spring Data автоматически сканировать все подпакеты модулей
 * (adapter.rest, adapter.persistence, adapter.git, adapter.kaiten, application)
 * без необходимости в явных {@code @ComponentScan}/{@code @EntityScan}/
 * {@code @EnableJpaRepositories}.</p>
 */
@SpringBootApplication
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
