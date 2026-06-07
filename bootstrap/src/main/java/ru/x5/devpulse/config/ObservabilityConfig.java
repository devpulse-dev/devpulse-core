package ru.x5.devpulse.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.x5.devpulse.application.port.out.CollectionRunRepository;

/**
 * Метрики наблюдаемости (Micrometer → {@code /actuator/metrics}).
 *
 * <p>Сейчас одна: свежесть {@code daily_stats}. Между падением прогона (после части сохранённых
 * батчей, но до финального recompute — см. ADR-10) и следующим успешным сбором агрегаты
 * устаревают молча. Gauge делает это окно видимым: алерт на рост
 * {@code devpulse.collection.staleness.seconds} ловит «давно не было успешного сбора»
 * (P2-2).</p>
 */
@Configuration
class ObservabilityConfig {

    /**
     * Gauge {@code devpulse.collection.staleness.seconds} — секунд с момента {@code until}
     * последнего УСПЕШНОГО сбора. {@code NaN}, если успешных сборов ещё не было.
     *
     * <p>{@link MeterBinder}-бин Spring Boot сам привязывает к {@code MeterRegistry}. Значение
     * считается на каждом scrape (запрос к {@code collection_run} — дёшево, индекс есть).</p>
     */
    @Bean
    MeterBinder collectionStalenessMetrics(CollectionRunRepository collectionRunRepository) {
        return registry -> Gauge.builder(
                        "devpulse.collection.staleness.seconds",
                        () -> stalenessSeconds(collectionRunRepository))
                .description("Секунд с момента until последнего успешного сбора (свежесть daily_stats)")
                .baseUnit("seconds")
                .strongReference(true)
                .register(registry);
    }

    private static double stalenessSeconds(CollectionRunRepository repo) {
        try {
            return repo.findLastSuccessfulUntil()
                    .map(until -> (double) Duration.between(until, LocalDateTime.now()).toSeconds())
                    .orElse(Double.NaN); // ни одного успешного сбора ещё не было
        } catch (RuntimeException e) {
            // gauge-функция не должна бросать на scrape (например БД недоступна) — отдаём NaN
            return Double.NaN;
        }
    }
}
