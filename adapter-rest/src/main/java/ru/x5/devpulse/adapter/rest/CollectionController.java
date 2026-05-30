package ru.x5.devpulse.adapter.rest;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.x5.devpulse.adapter.rest.api.CollectionApi;
import ru.x5.devpulse.adapter.rest.api.model.CollectionRun;
import ru.x5.devpulse.adapter.rest.api.model.CollectionRunRequest;
import ru.x5.devpulse.adapter.rest.mapper.CollectionRunMapper;
import ru.x5.devpulse.application.port.in.CollectDailyStatsUseCase;
import ru.x5.devpulse.application.port.in.GetCollectionRunUseCase;

/**
 * Эндпоинты управления прогонами сбора. Контракт — {@link CollectionApi},
 * сгенерированный из {@code collection-api.yaml}.
 *
 * <p>{@code POST /api/v2/collection/runs} запускает сбор синхронно. При попытке
 * параллельного запуска срабатывает {@code pg_try_advisory_lock} и
 * {@link ru.x5.devpulse.application.port.out.CollectionAlreadyRunningException}
 * мапится в 409 через {@code ApiExceptionHandler}.</p>
 */
@RestController
@RequiredArgsConstructor
class CollectionController implements CollectionApi {

    private final CollectDailyStatsUseCase collectDailyStats;
    private final GetCollectionRunUseCase getCollectionRun;
    private final CollectionRunMapper collectionRunMapper;

    @Override
    public ResponseEntity<CollectionRun> startCollectionRun(CollectionRunRequest body) {
        var since = body == null ? null : body.getSince();
        return ResponseEntity.ok(collectionRunMapper.toDto(collectDailyStats.run(since)));
    }

    @Override
    public ResponseEntity<CollectionRun> getCollectionRun(UUID id) {
        return getCollectionRun.findById(id)
                .map(collectionRunMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
