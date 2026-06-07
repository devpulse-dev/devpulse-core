package ru.x5.devpulse.application.service;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.x5.devpulse.application.port.in.CancelCollectionUseCase;
import ru.x5.devpulse.application.port.out.CollectionRunNotCancellableException;
import ru.x5.devpulse.application.port.out.CollectionRunRepository;
import ru.x5.devpulse.domain.model.collection.CollectionRun;

/**
 * Реализация {@link CancelCollectionUseCase}: ставит флаг отмены на RUNNING-прогон.
 *
 * <p>Сам сбор остановится на ближайшем checkpoint'е (между репозиториями / фазами) —
 * см. {@code CollectDailyStatsService}. Флаг в БД ⇒ работает кросс-инстансно.</p>
 */
@Slf4j
@RequiredArgsConstructor
public final class CancelCollectionService implements CancelCollectionUseCase {

    private final CollectionRunRepository collectionRunRepository;

    @Override
    public Optional<CollectionRun> cancel(UUID runId) {
        Optional<CollectionRun> runOpt = collectionRunRepository.findById(runId);
        if (runOpt.isEmpty()) {
            return Optional.empty();
        }
        CollectionRun run = runOpt.get();
        if (run.status().isTerminal()) {
            throw new CollectionRunNotCancellableException(runId, run.status());
        }
        collectionRunRepository.markCancelRequested(runId);
        log.info("Запрошена отмена прогона {} — остановится на ближайшем checkpoint'е", runId);
        return Optional.of(run);
    }
}
