package ru.x5.devpulse.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.x5.devpulse.application.port.out.BackgroundRunner;

/**
 * Инфраструктура фонового исполнения сбора (async POST).
 */
@Configuration
class BackgroundExecutionConfig {

    /**
     * {@link BackgroundRunner} поверх virtual-thread-per-task executor'а.
     *
     * <p><b>Намеренно НЕ Spring-managed lifecycle:</b> executor захвачен в замыкании, не
     * выставлен бином → Spring не вызовет на нём {@code shutdown()/close()} при остановке.
     * Иначе graceful shutdown ждал бы завершения многочасового сбора. На остановке JVM
     * фон-поток умирает, прогон остаётся {@code RUNNING} → его подхватит startup-реконсиляция
     * (ADR-13). Virtual threads не держат ресурсов в простое — «утечки» executor'а нет.</p>
     *
     * <p>Сбор single-flight (advisory lock), так что одновременно работает максимум одна задача.</p>
     */
    @Bean
    BackgroundRunner backgroundRunner() {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        return executor::execute;
    }
}
