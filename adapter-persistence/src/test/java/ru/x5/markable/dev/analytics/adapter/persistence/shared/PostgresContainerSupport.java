package ru.x5.markable.dev.analytics.adapter.persistence.shared;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Базовый класс: общий статический PostgreSQL-контейнер для всех IT-тестов модуля.
 *
 * <p>Контейнер стартует один раз на JVM (static final), затем все тесты переиспользуют его.
 * Это в разы быстрее чем поднимать новый контейнер на каждый класс.</p>
 *
 * <p>Liquibase прогоняет миграции автоматически при старте Spring-контекста.</p>
 */
public abstract class PostgresContainerSupport {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("devanalytics_test")
            .withUsername("test")
            .withPassword("test");

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
