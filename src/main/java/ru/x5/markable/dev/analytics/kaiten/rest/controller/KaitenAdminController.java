package ru.x5.markable.dev.analytics.kaiten.rest.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenSyncRequest;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenCardCollectorService;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenUserSyncService;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/kaiten/admin")
@RequiredArgsConstructor
@Log4j2
public class KaitenAdminController {

    private final KaitenUserSyncService userSyncService;
    private final KaitenCardCollectorService cardCollectorService;

    @PostMapping("/sync/users")
    public ResponseEntity<String> syncUsers() {
        userSyncService.syncAllUsers();
        return ResponseEntity.ok("Users sync started");
    }

    @PostMapping("/sync/cards")
    public ResponseEntity<String> syncCards(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since
    ) {
        LocalDateTime syncSince = since != null ? since : LocalDateTime.now().minusDays(30);
        cardCollectorService.collectCardsFromAllSpaces(syncSince);
        return ResponseEntity.ok("Cards collection started");
    }

    @PostMapping("/sync/team")
    public ResponseEntity<String> syncTeamCards(@RequestBody KaitenSyncRequest request) {
        LocalDateTime syncSince = request.getSince() != null ? request.getSince() : LocalDateTime.now().minusDays(30);
        cardCollectorService.collectCardsForTeam(request.getTeamEmails(), syncSince);
        return ResponseEntity.ok("Team cards collection started");
    }
}
