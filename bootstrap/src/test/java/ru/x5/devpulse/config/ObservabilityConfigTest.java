package ru.x5.devpulse.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.x5.devpulse.application.port.out.CollectionRunRepository;

@DisplayName("ObservabilityConfig: gauge свежести daily_stats")
class ObservabilityConfigTest {

    private static final String METRIC = "devpulse.collection.staleness.seconds";

    @Test
    @DisplayName("Gauge = секунды с until последнего успешного сбора")
    void reportsSecondsSinceLastSuccess() {
        CollectionRunRepository repo = mock(CollectionRunRepository.class);
        when(repo.findLastSuccessfulUntil())
                .thenReturn(Optional.of(LocalDateTime.now().minusMinutes(5)));

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new ObservabilityConfig().collectionStalenessMetrics(repo).bindTo(registry);

        double value = registry.get(METRIC).gauge().value();
        assertThat(value).as("≈300 секунд (5 минут)").isBetween(290.0, 360.0);
    }

    @Test
    @DisplayName("Gauge = NaN, если успешных сборов ещё не было")
    void reportsNaNWhenNoSuccessfulRun() {
        CollectionRunRepository repo = mock(CollectionRunRepository.class);
        when(repo.findLastSuccessfulUntil()).thenReturn(Optional.empty());

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new ObservabilityConfig().collectionStalenessMetrics(repo).bindTo(registry);

        assertThat(registry.get(METRIC).gauge().value()).isNaN();
    }
}
