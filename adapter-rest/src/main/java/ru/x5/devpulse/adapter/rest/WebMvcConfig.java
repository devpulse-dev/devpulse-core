package ru.x5.devpulse.adapter.rest;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Подкладывает префикс {@code /api/v2} ко всем {@code @RestController}-ам в
 * {@code adapter.rest}, сгенерированным из OpenAPI-контрактов.
 *
 * <p>OpenAPI спеки описывают пути без префикса ({@code /collection/runs},
 * {@code /dashboard}, ...) — он указан только в {@code servers.url}.
 * Сгенерированные {@code *Api}-интерфейсы переносят пути 1-в-1, поэтому
 * version-префикс ставим централизованно на уровне Spring MVC.</p>
 *
 * <p><b>Фильтр:</b> два условия (и).
 * <ol>
 *   <li>Класс в пакете {@code adapter.rest..} — actuator/management остаются на корне.</li>
 *   <li>Имеет {@code @RestController} — защита от случайного {@code @Controller} (admin UI,
 *       SSR-страницы), который мог бы появиться в этом пакете и невольно подцепить префикс.</li>
 * </ol>
 * Это сильнее чем package-only filter (см. OAS-4 в review): админ-страница в этом пакете
 * не подцепит {@code /api/v2}, потому что у неё {@code @Controller}, а не {@code @RestController}.</p>
 */
@Configuration
class WebMvcConfig implements WebMvcConfigurer {

    private static final String REST_BASE_PACKAGE = "ru.x5.devpulse.adapter.rest";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api/v2", c ->
                c.getPackageName().startsWith(REST_BASE_PACKAGE)
                        && AnnotationUtils.findAnnotation(c, RestController.class) != null);
    }
}
