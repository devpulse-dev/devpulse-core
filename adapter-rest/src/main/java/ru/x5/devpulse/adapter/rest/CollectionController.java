package ru.x5.devpulse.adapter.rest;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.x5.devpulse.adapter.rest.api.CollectionApi;
import ru.x5.devpulse.adapter.rest.api.model.CollectionRun;
import ru.x5.devpulse.adapter.rest.api.model.CollectionRunRequest;
import ru.x5.devpulse.adapter.rest.mapper.CollectionRunMapper;
import ru.x5.devpulse.application.port.in.CancelCollectionUseCase;
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
    private final CancelCollectionUseCase cancelCollection;
    private final GetCollectionRunUseCase getCollectionRun;
    private final CollectionRunMapper collectionRunMapper;

    @Override
    public ResponseEntity<CollectionRun> startCollectionRun(CollectionRunRequest body) {
        var since = body == null ? null : body.getSince();
        return ResponseEntity.ok(collectionRunMapper.toDto(collectDailyStats.run(since)));
    }

    /**
     * Отмена прогона. Асинхронно: 202 + run (RUNNING с поднятым флагом), фактическая остановка —
     * на ближайшем checkpoint'е. 404 если прогона нет; 409 (через ApiExceptionHandler) если он
     * уже терминальный.
     */
    @Override
    public ResponseEntity<CollectionRun> cancelCollectionRun(UUID id) {
        return cancelCollection.cancel(id)
                .map(collectionRunMapper::toDto)
                .map(dto -> ResponseEntity.status(HttpStatus.ACCEPTED).body(dto))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<CollectionRun> getCollectionRun(UUID id) {
        return getCollectionRun.findById(id)
                .map(collectionRunMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Самый свежий / идущий прогон — источник правды для фронта по id живого сбора.
     * 200 + run; 404 если прогонов ещё не было.
     */
    @Override
    public ResponseEntity<CollectionRun> getLatestCollectionRun() {
        return getCollectionRun.findLatest()
                .map(collectionRunMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
