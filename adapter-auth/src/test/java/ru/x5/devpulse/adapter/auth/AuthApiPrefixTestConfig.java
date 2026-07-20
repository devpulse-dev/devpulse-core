package ru.x5.devpulse.adapter.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * В проде префикс {@code /api/v2} навешивает {@code WebMvcConfig} из adapter-rest (по пакету,
 * включая {@code adapter.auth}). В {@code @WebMvcTest}-слайсе adapter-auth того конфига нет
 * (adapter-rest не на classpath), поэтому реплицируем тот же префикс для adapter.auth — чтобы
 * пути контроллера совпали с security-матчерами ({@code /api/v2/auth/*}).
 */
@TestConfiguration
class AuthApiPrefixTestConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api/v2", c ->
                c.getPackageName().startsWith("ru.x5.devpulse.adapter.auth")
                        && AnnotationUtils.findAnnotation(c, RestController.class) != null);
    }
}
