package ru.x5.devpulse.adapter.persistence.shared;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Базовый класс: общий статический PostgreSQL-контейнер для всех IT-тестов модуля.
 *
 * <p><b>Singleton-паттерн Testcontainers:</b> контейнер стартует один раз в static-блоке,
 * JVM shutdown hook (Ryuk + JVM hook) убирает его при завершении JVM. Все тесты в одной JVM
 * переиспользуют один контейнер — на порядок быстрее, чем поднимать на каждый класс.</p>
 *
 * <p><b>Намеренно НЕ используем</b> {@code @Container} и {@code @Testcontainers}-extension
 * в subclass-ах: их JUnit-extension останавливает контейнер на {@code @AfterAll} тест-класса,
 * а следующий класс получает новый контейнер на новом порту — Spring при этом держит закэшированный
 * контекст со старым URL и валится с «connection refused».</p>
 *
 * <p>Liquibase прогоняет миграции автоматически при старте Spring-контекста.</p>
 */
public abstract class PostgresContainerSupport {

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
