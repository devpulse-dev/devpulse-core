package ru.x5.markable.dev.analytics.adapter.persistence.shared;

import java.time.Duration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;

/**
 * Базовый класс: общий статический PostgreSQL-контейнер для всех IT-тестов модуля.
 *
 * <p>Контейнер стартует один раз на JVM (static final), затем все тесты переиспользуют его.
 * Это в разы быстрее чем поднимать новый контейнер на каждый класс.</p>
 *
 * <p>Liquibase прогоняет миграции автоматически при старте Spring-контекста.</p>
 *
 * <p><b>Wait strategy:</b> Postgres-логи пишут «database system is ready to accept connections»
 * дважды (init + после старта приёма соединений). Ждём именно второе сообщение — иначе на
 * медленном CI Hikari может попробовать подключиться слишком рано и получить connection refused,
 * хотя порт уже замаплен.</p>
 */
public abstract class PostgresContainerSupport {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("devanalytics_test")
            .withUsername("test")
            .withPassword("test")
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        // Datasource — динамически из контейнера, остальное (liquibase/jpa) в application.yml
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
