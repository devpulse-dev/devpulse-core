package ru.x5.markable.dev.analytics.adapter.persistence.collection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.x5.markable.dev.analytics.adapter.persistence.shared.PostgresContainerSupport;
import ru.x5.markable.dev.analytics.application.port.out.CollectionRunRepository;
import ru.x5.markable.dev.analytics.domain.model.collection.CollectionRun;

@SpringBootTest
@Testcontainers
@DisplayName("CollectionRunRepositoryAdapter (журнал запусков сбора)")
class CollectionRunRepositoryAdapterIT extends PostgresContainerSupport {

    private static final LocalDateTime SINCE = LocalDateTime.of(2026, 6, 1, 0, 0);
    private static final LocalDateTime UNTIL_EARLY = LocalDateTime.of(2026, 6, 10, 0, 0);
    private static final LocalDateTime UNTIL_LATE = LocalDateTime.of(2026, 6, 20, 0, 0);
    private static final LocalDateTime UNTIL_FAILED = LocalDateTime.of(2026, 7, 1, 0, 0);

    @Autowired
    CollectionRunRepository repo;

    @Test
    @DisplayName("findLastSuccessfulUntil возвращает максимум untilDate только среди SUCCESS-запусков")
    void persistsAndReadsLastSuccessfulUntil() {
        repo.save(CollectionRun.start(SINCE, UNTIL_EARLY).succeeded());
        repo.save(CollectionRun.start(SINCE, UNTIL_LATE).succeeded());
        // Failed-запуск с более поздним untilDate не должен сбивать max(SUCCESS.until)
        repo.save(CollectionRun.start(SINCE, UNTIL_FAILED).failed("oops"));

        assertThat(repo.findLastSuccessfulUntil())
                .as("из двух SUCCESS — должен вернуться более поздний untilDate")
                .contains(UNTIL_LATE);
    }
}
