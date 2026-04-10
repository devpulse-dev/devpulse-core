package ru.x5.markable.dev.analytics.gitlab.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Конфигурация асинхронного выполнения задач.
 * 
 * <p>Настраивает пул потоков для асинхронного выполнения задач анализа репозиториев.
 * Позволяет выполнять анализ нескольких репозиториев параллельно.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Создает пул потоков для асинхронного выполнения задач анализа.
     * 
     * <p>Использует фиксированный пул из 5 потоков для параллельного анализа репозиториев.</p>
     * 
     * @return исполнитель задач (Executor) с фиксированным пулом потоков
     */
    @Bean(name = "analysisExecutor")
    public Executor analysisExecutor() {
        return Executors.newFixedThreadPool(5);
    }

}
