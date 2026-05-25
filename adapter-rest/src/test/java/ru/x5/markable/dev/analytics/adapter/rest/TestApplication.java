package ru.x5.markable.dev.analytics.adapter.rest;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Минимальная точка сборки контекста для тестов adapter-rest.
 *
 * <p>{@code @WebMvcTest} ищет {@code @SpringBootConfiguration} от пакета теста вверх
 * И стартует от него {@code @ComponentScan} нужного пакета. Поэтому здесь именно
 * {@code @SpringBootApplication} (= {@code @SpringBootConfiguration} +
 * {@code @EnableAutoConfiguration} + {@code @ComponentScan}), а не сокращённая связка.</p>
 *
 * <p>Настоящий {@code @SpringBootApplication} живёт в модуле {@code bootstrap},
 * который сюда не виден и не должен быть виден — слои не нарушаем. Класс лежит
 * в test-sources, ArchUnit его не видит.</p>
 */
@SpringBootApplication
class TestApplication {
}
