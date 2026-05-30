package ru.x5.devpulse.adapter.rest;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Подкладывает префикс {@code /api/v2} ко всем {@code @RequestMapping}-ам,
 * сгенерированным из OpenAPI-контрактов.
 *
 * <p>OpenAPI спеки описывают пути без префикса ({@code /collection/runs},
 * {@code /dashboard}, ...) — он указан только в {@code servers.url}.
 * Сгенерированные {@code *Api}-интерфейсы переносят пути 1-в-1, поэтому
 * version-префикс надо ставить отдельно на уровне Spring MVC.</p>
 *
 * <p>Используется {@code PathMatchConfigurer.addPathPrefix} (а не
 * {@code server.servlet.context-path}), чтобы префикс касался только REST-
 * контроллеров, а actuator/management-эндпоинты остались на корне.</p>
 */
@Configuration
class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api/v2", c -> c.getPackageName().startsWith("ru.x5.devpulse.adapter.rest"));
    }
}
