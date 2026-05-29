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
     * Момент конца последнего успешного сбора — точка старта для следующего.
     */
    Optional<LocalDateTime> findLastSuccessfulUntil();
}
