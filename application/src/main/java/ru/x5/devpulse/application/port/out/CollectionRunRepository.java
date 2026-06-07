package ru.x5.devpulse.application.port.out;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import ru.x5.devpulse.domain.model.collection.CollectionRun;

/**
 * Port out: журнал запусков сбора.
 */
public interface CollectionRunRepository {

    void save(CollectionRun run);

    Optional<CollectionRun> findById(UUID id);

    /**
     * Самый свежий прогон по {@code startedAt} (идущий, если есть — он же и самый свежий,
     * т.к. сбор single-flight). {@link Optional#empty()} если прогонов ещё не было.
     */
    Optional<CollectionRun> findLatest();

    /**
     * Момент конца последнего успешного сбора — точка старта для следующего.
     */
    Optional<LocalDateTime> findLastSuccessfulUntil();

    /**
     * Переводит все «осиротевшие» {@code RUNNING}-прогоны в {@code FAILED} (вызывается на старте
     * под advisory-локом: если лок свободен — ни один сбор не идёт, значит RUNNING — фантом от
     * упавшего процесса).
     *
     * @return сколько прогонов переведено
     */
    int failOrphanedRunning();

    /** Ставит флаг кооперативной отмены прогона (cancel-эндпоинт). */
    void markCancelRequested(UUID id);

    /**
     * Запрошена ли отмена прогона. Читается бегущим сбором на checkpoint'ах.
     * Несуществующий id → {@code false}.
     */
    boolean isCancelRequested(UUID id);
}
