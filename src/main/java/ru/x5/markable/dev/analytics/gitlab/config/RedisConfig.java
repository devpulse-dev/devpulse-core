package ru.x5.markable.dev.analytics.gitlab.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация кэширования с использованием Redis.
 * 
 * <p>Включает поддержку кэширования в приложении через Spring Cache abstraction.
 * Используется для кэширования результатов запросов к внешним API и базы данных.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Configuration
@EnableCaching
public class RedisConfig {

}
