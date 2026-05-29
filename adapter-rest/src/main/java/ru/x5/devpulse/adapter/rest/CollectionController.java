package ru.x5.devpulse.adapter.rest;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.x5.devpulse.adapter.rest.dto.CollectionRunResponse;
import ru.x5.devpulse.adapter.rest.dto.StartCollectionRequest;
import ru.x5.devpulse.application.port.in.CollectDailyStatsUseCase;
import ru.x5.devpulse.application.port.in.GetCollectionRunUseCase;

/**
 * Эндпоинты управления прогонами сбора.
 *
 * <p>{@code POST /api/v2/collection/runs} запускает сбор синхронно — отдаёт уже финальный
 * результат ({@code SUCCESS} или {@code FAILED}). Долгий ответ — намеренный trade-off:
 * пока сбор идёт &lt; 1 минуты, явный sync API проще, чем 202 + polling.
 * При увеличении времени переедем на async.</p>
 */
@RestController
@RequestMapping("/api/v2/collection/runs")
@RequiredArgsConstructor
public class CollectionController {

    private final CollectDailyStatsUseCase collectDailyStats;
    private final GetCollectionRunUseCase getCollectionRun;

    @PostMapping
    public ResponseEntity<CollectionRunResponse> start(@RequestBody(required = false) StartCollectionRequest request) {
        var since = request == null ? null : request.since();
        return ResponseEntity.ok(CollectionRunResponse.from(collectDailyStats.run(since)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CollectionRunResponse> get(@PathVariable UUID id) {
        return getCollectionRun.findById(id)
                .map(CollectionRunResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
