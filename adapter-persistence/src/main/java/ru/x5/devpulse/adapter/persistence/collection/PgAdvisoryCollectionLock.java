package ru.x5.devpulse.adapter.persistence.collection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.x5.devpulse.application.port.out.CollectionAlreadyRunningException;
import ru.x5.devpulse.application.port.out.CollectionLock;

/**
 * Реализация {@link CollectionLock} через Postgres advisory lock.
 *
 * <p><b>Семантика:</b> session-level lock через {@code pg_try_advisory_lock(key)}. Lock держится
 * пока удерживается {@link Connection}; на закрытии connection (или падении сборщика, обрыве
 * сети, kill -9) Postgres сам отпускает lock. Это страховка от «забытого» lock'а — нам не
 * нужно ловить все возможные пути выхода в Java-коде.</p>
 *
 * <p><b>Connection lifecycle:</b> мы берём connection из пула и держим её до закрытия handle.
 * Один сбор = один connection из HikariCP на всё время сбора (минуты-часы). При pool size 20
 * это даёт 19 connections для запросов фронта — достаточно.</p>
 *
 * <p><b>Ключ:</b> 64-битное магическое число для именованного lock'а. Если в будущем
 * появятся другие advisory locks — здесь стоит реестр.</p>
 */
@Component
@Log4j2
@RequiredArgsConstructor
class PgAdvisoryCollectionLock implements CollectionLock {

    /**
     * Стабильный 64-битный ключ для advisory lock на collection run.
     *
     * <p>Значение произвольное, главное чтобы оно было уникальным среди всех advisory locks,
     * которые приложение использует. Сейчас он один.</p>
     */
    private static final long COLLECTION_LOCK_KEY = 0x4445_5650_554C_5345L; // "DEVPULSE" в ASCII

    private final DataSource dataSource;

    @Override
    public Handle acquireOrThrow() {
        Connection conn;
        try {
            conn = dataSource.getConnection();
            // autoCommit true — нам не нужна транзакция вокруг session lock'а
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось взять JDBC connection для advisory lock", e);
        }

        boolean acquired = false;
        try {
            acquired = tryLock(conn);
        } catch (SQLException e) {
            quietlyClose(conn);
            throw new IllegalStateException("Сбой pg_try_advisory_lock", e);
        }

        if (!acquired) {
            quietlyClose(conn);
            log.warn("Advisory lock {} занят — отказ в запуске сбора", COLLECTION_LOCK_KEY);
            throw new CollectionAlreadyRunningException();
        }

        log.info("Advisory lock {} взят — сбор может стартовать", COLLECTION_LOCK_KEY);
        return new PgHandle(conn);
    }

    private static boolean tryLock(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            ps.setLong(1, COLLECTION_LOCK_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private static void quietlyClose(Connection conn) {
        try {
            conn.close();
        } catch (SQLException ignore) {
            // pool close failures не должны маскировать исходную ошибку
        }
    }

    /**
     * Handle: при close() явно отпускает lock и возвращает connection в пул.
     *
     * <p>Явный {@code pg_advisory_unlock} — не строго обязательный (закрытие connection
     * тоже отпустит lock), но он быстрее: connection вернётся в pool без удерживаемого lock'а,
     * который иначе бы держался до physical close сессии (а pooled connection переиспользуется).</p>
     */
    @RequiredArgsConstructor
    private static final class PgHandle implements Handle {

        private final Connection conn;
        private boolean closed = false;

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            try (PreparedStatement ps = conn.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                ps.setLong(1, COLLECTION_LOCK_KEY);
                ps.executeQuery().close();
            } catch (SQLException e) {
                log.warn("Не удалось явно освободить advisory lock; полагаемся на закрытие connection: {}",
                        e.getMessage());
            } finally {
                quietlyClose(conn);
            }
        }
    }
}
