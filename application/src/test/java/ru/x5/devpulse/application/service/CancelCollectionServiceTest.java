package ru.x5.devpulse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.x5.devpulse.application.port.out.CollectionRunNotCancellableException;
import ru.x5.devpulse.application.port.out.CollectionRunRepository;
import ru.x5.devpulse.domain.model.collection.CollectionRun;

@ExtendWith(MockitoExtension.class)
@DisplayName("CancelCollectionService")
class CancelCollectionServiceTest {

    private static final LocalDateTime SINCE = LocalDateTime.of(2026, 5, 1, 0, 0);
    private static final LocalDateTime UNTIL = LocalDateTime.of(2026, 5, 31, 23, 59);

    @Mock CollectionRunRepository repo;

    private CancelCollectionService service() {
        return new CancelCollectionService(repo);
    }

    @Test
    @DisplayName("RUNNING прогон → ставит флаг отмены, возвращает run")
    void cancelsRunningRun() {
        CollectionRun running = CollectionRun.start(SINCE, UNTIL);
        when(repo.findById(running.id())).thenReturn(Optional.of(running));

        Optional<CollectionRun> result = service().cancel(running.id());

        assertAll("RUNNING отменяется",
                () -> assertThat(result).contains(running),
                () -> verify(repo).markCancelRequested(running.id()));
    }

    @Test
    @DisplayName("Терминальный прогон → 409-исключение, флаг не ставится")
    void terminalRunNotCancellable() {
        CollectionRun done = CollectionRun.start(SINCE, UNTIL).succeeded();
        when(repo.findById(done.id())).thenReturn(Optional.of(done));

        assertAll("терминальный нельзя отменить",
                () -> assertThatThrownBy(() -> service().cancel(done.id()))
                        .isInstanceOf(CollectionRunNotCancellableException.class),
                () -> verify(repo, never()).markCancelRequested(any()));
    }

    @Test
    @DisplayName("Нет прогона → empty, флаг не ставится")
    void missingRunReturnsEmpty() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertAll("неизвестный id",
                () -> assertThat(service().cancel(id)).isEmpty(),
                () -> verify(repo, never()).markCancelRequested(any()));
    }
}
