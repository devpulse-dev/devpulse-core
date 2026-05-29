package ru.x5.devpulse.adapter.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.x5.devpulse.adapter.rest.dto.SyncKaitenUsersResponse;
import ru.x5.devpulse.application.port.in.SyncKaitenUsersUseCase;

/** Принудительные операции синхронизации Kaiten. */
@RestController
@RequestMapping("/api/v2/kaiten")
@RequiredArgsConstructor
public class KaitenController {

    private final SyncKaitenUsersUseCase syncKaitenUsers;

    @PostMapping("/sync-users")
    public ResponseEntity<SyncKaitenUsersResponse> syncUsers() {
        return ResponseEntity.ok(new SyncKaitenUsersResponse(syncKaitenUsers.syncAll()));
    }
}
