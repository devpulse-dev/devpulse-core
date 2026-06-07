package ru.x5.devpulse.adapter.persistence.collection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.x5.devpulse.adapter.persistence.shared.PostgresContainerSupport;
import ru.x5.devpulse.application.port.out.CollectionRunRepository;
import ru.x5.devpulse.domain.model.collection.CollectionRun;
import ru.x5.devpulse.domain.model.collection.CollectionStatus;

@SpringBootTest
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

    @Test
    @DisplayName("CANCELLED не двигает курсор (findLastSuccessfulUntil его игнорирует, как FAILED)")
    void cancelledDoesNotAdvanceCursor() {
        repo.save(CollectionRun.start(SINCE, UNTIL_EARLY).succeeded());
        // Отменённый прогон с более поздним until не должен стать новой точкой старта.
        repo.save(CollectionRun.start(SINCE, UNTIL_LATE).cancelled("отменён оператором"));

        assertThat(repo.findLastSuccessfulUntil())
                .as("CANCELLED игнорируется — курсор остаётся на SUCCESS")
                .contains(UNTIL_EARLY);
    }

    @Test
    @DisplayName("findLatest = самый свежий по startedAt; идущий (RUNNING) — поверх терминальных")
    void findLatestReturnsMostRecentRunning() {
        // Явно future-dated startedAt — гарантированно «самый свежий» в общей БД (другие тесты
        // вставляют прогоны с now()). RUNNING не влияет на findLastSuccessfulUntil (только SUCCESS).
        LocalDateTime future = LocalDateTime.of(2099, 1, 1, 0, 0);
        CollectionRun running = new CollectionRun(
                UUID.randomUUID(), future, null, SINCE, UNTIL_LATE,
                CollectionStatus.RUNNING, null);
        repo.save(running);

        assertThat(repo.findLatest())
                .as("самый свежий по startedAt — наш RUNNING")
                .map(CollectionRun::id)
                .contains(running.id());
    }

    @Test
    @DisplayName("markCancelRequested / isCancelRequested round-trip; неизвестный id → false")
    void cancelFlagRoundTrip() {
        CollectionRun run = CollectionRun.start(SINCE, UNTIL_EARLY);
        repo.save(run);

        assertThat(repo.isCancelRequested(run.id()))
                .as("свежий прогон — флаг не поднят").isFalse();

        repo.markCancelRequested(run.id());

        assertThat(repo.isCancelRequested(run.id()))
                .as("после markCancelRequested — поднят").isTrue();
        assertThat(repo.isCancelRequested(java.util.UUID.randomUUID()))
                .as("неизвестный id → false (без NPE)").isFalse();
    }
}
