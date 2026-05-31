package ru.x5.devpulse.adapter.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.x5.devpulse.adapter.rest.api.KaitenApi;
import ru.x5.devpulse.adapter.rest.api.model.KaitenSyncResponse;
import ru.x5.devpulse.adapter.rest.mapper.KaitenSyncResponseMapper;
import ru.x5.devpulse.application.port.in.SyncKaitenUsersUseCase;

/**
 * Принудительные операции синхронизации Kaiten. Контракт — {@link KaitenApi},
 * сгенерированный из {@code kaiten-api.yaml}.
 */
@RestController
@RequiredArgsConstructor
class KaitenController implements KaitenApi {

    private final SyncKaitenUsersUseCase syncKaitenUsers;
    private final KaitenSyncResponseMapper syncResponseMapper;

    @Override
    public ResponseEntity<KaitenSyncResponse> syncKaitenUsers() {
        return ResponseEntity.ok(syncResponseMapper.toDto(syncKaitenUsers.syncAll()));
    }
}
