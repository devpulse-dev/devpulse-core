package ru.x5.devpulse.adapter.rest;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import ru.x5.devpulse.adapter.rest.mapper.DomainTypeConverters;

/**
 * Тестовый конфиг: подгружает все MapStruct-мапперы в WebMvc-slice контекст.
 *
 * <p>{@code @WebMvcTest} по умолчанию фильтрует scan на {@code @Controller}/
 * {@code @RestControllerAdvice}/etc., поэтому MapStruct-сгенерённые
 * {@code @Component}-ы из {@code adapter.rest.mapper} в slice не попадают.
 * Подключение этого конфига через {@code @Import(RestMappersTestConfig.class)}
 * решает проблему через явный {@link ComponentScan} на нужный пакет.</p>
 */
@TestConfiguration
@ComponentScan(basePackageClasses = DomainTypeConverters.class)
class RestMappersTestConfig {
}
