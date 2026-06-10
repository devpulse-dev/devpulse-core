package ru.x5.devpulse.application.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.x5.devpulse.application.port.out.CollectionAlreadyRunningException;
import ru.x5.devpulse.application.port.out.CollectionLock;
import ru.x5.devpulse.application.port.out.CollectionRunRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("StartupReconciliationService (осиротевшие RUNNING при старте)")
class StartupReconciliationServiceTest {

    @Mock CollectionLock lock;
    @Mock CollectionLock.Handle handle;
    @Mock CollectionRunRepository repo;

    private StartupReconciliationService service() {
        return new StartupReconciliationService(lock, repo);
    }

    @Test
    @DisplayName("Лок свободен → фантомные RUNNING переводятся в FAILED, лок освобождается")
    void reconcilesWhenLockFree() {
        when(lock.acquireOrThrow()).thenReturn(handle);
        when(repo.failOrphanedRunning()).thenReturn(2);

        service().reconcileOrphanedRuns();

        verify(repo).failOrphanedRunning();
        verify(handle).close(); // try-with-resources освобождает advisory-lock
    }

    @Test
    @DisplayName("Лок занят (сбор идёт на другом инстансе) → RUNNING НЕ трогаем")
    void skipsWhenLockHeld() {
        when(lock.acquireOrThrow()).thenThrow(new CollectionAlreadyRunningException());

        service().reconcileOrphanedRuns();

        verify(repo, never()).failOrphanedRunning();
    }
}
