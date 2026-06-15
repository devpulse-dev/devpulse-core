package ru.x5.devpulse.adapter.auth;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Минимальная точка сборки контекста для тестов adapter-auth.
 *
 * <p>{@code @WebMvcTest} ищет {@code @SpringBootConfiguration} от пакета теста вверх и стартует
 * от него {@code @ComponentScan}. Поэтому здесь {@code @SpringBootApplication}. Настоящий
 * {@code @SpringBootApplication} живёт в {@code bootstrap}, который сюда не виден (слои не
 * нарушаем). Класс — в test-sources, ArchUnit его не видит.</p>
 */
@SpringBootApplication
class TestApplication {
}
