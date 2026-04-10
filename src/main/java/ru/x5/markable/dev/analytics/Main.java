package ru.x5.markable.dev.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Главный класс приложения Markable Dev Analytics.
 * 
 * <p>Система аналитики активности разработчиков в Git-репозиториях с интеграцией AI для генерации сводок.</p>
 * 
 * <p>Основные возможности:</p>
 * <ul>
 *   <li>Сбор статистики коммитов из Git-репозиториев</li>
 *   <li>Агрегация данных по дням, неделям и периодам</li>
 *   <li>Интеграция с системой управления задачами Kaiten</li>
 *   <li>Генерация AI-сводок на основе статистики пользователей</li>
 *   <li>Предоставление REST API для получения аналитических данных</li>
 * </ul>
 * 
 * <p>Конфигурация:</p>
 * <ul>
 *   <li>{@link EnableScheduling} - включает планирование задач (например, ежедневный сбор статистики)</li>
 *   <li>{@link EnableRetry} - включает механизм повторных попыток для внешних вызовов</li>
 * </ul>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@SpringBootApplication
@EnableScheduling
@EnableRetry
public class Main {

    /**
     * Точка входа в приложение.
     * 
     * <p>Запускает Spring Boot приложение с переданными аргументами командной строки.</p>
     * 
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

}