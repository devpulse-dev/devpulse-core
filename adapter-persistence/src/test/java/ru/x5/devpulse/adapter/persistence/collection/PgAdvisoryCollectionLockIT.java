package ru.x5.devpulse.adapter.persistence.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.x5.devpulse.adapter.persistence.shared.PostgresContainerSupport;
import ru.x5.devpulse.application.port.out.CollectionAlreadyRunningException;
import ru.x5.devpulse.application.port.out.CollectionLock;

/**
 * Integration: Postgres advisory lock реально удерживается на уровне БД.
 *
 * <p>Сценарии:
 * <ul>
 *   <li>взять lock → второй {@code acquireOrThrow()} бросает 409</li>
 *   <li>отпустить lock → следующий вызов проходит</li>
 *   <li>не отпускать handle вручную (имитация падения) → close() в try-with-resources всё равно
 *       освобождает lock</li>
 * </ul></p>
 */
@SpringBootTest
@DisplayName("PgAdvisoryCollectionLock (session-level lock через pg_try_advisory_lock)")
class PgAdvisoryCollectionLockIT extends PostgresContainerSupport {

    @Autowired
    CollectionLock lock;

    @Test
    @DisplayName("Второй acquireOrThrow пока первый держится → CollectionAlreadyRunningException")
    void secondAcquireFailsWhileFirstHeld() {
        try (CollectionLock.Handle first = lock.acquireOrThrow()) {
            assertThat(first).isNotNull();

            assertThatThrownBy(() -> lock.acquireOrThrow())
                    .isInstanceOf(CollectionAlreadyRunningException.class);
        }
    }

    @Test
    @DisplayName("После close() lock снова свободен")
    void releaseAllowsNextAcquire() {
        try (CollectionLock.Handle ignored = lock.acquireOrThrow()) {
            // удерживаем
        }
        // следующий должен пройти
        try (CollectionLock.Handle next = lock.acquireOrThrow()) {
            assertThat(next).isNotNull();
        }
    }

    @Test
    @DisplayName("Идемпотентность close(): двойной close() не падает")
    void doubleCloseIsSafe() {
        CollectionLock.Handle handle = lock.acquireOrThrow();
        assertAll("close идемпотентен",
                () -> handle.close(),
                () -> handle.close());
        // и после двойного close lock свободен — берём заново
        try (CollectionLock.Handle next = lock.acquireOrThrow()) {
            assertThat(next).isNotNull();
        }
    }
}
