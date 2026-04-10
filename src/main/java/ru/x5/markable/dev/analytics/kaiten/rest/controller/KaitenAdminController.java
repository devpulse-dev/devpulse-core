package ru.x5.markable.dev.analytics.kaiten.rest.controller;

import java.time.LocalDateTime;
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

/**
 * REST-контроллер для административных операций с Kaiten.
 * 
 * <p>Предоставляет API для синхронизации пользователей и карточек
 * из системы Kaiten в локальную базу данных.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/v1/kaiten/admin")
@RequiredArgsConstructor
@Log4j2
public class KaitenAdminController {

    /**
     * Сервис для синхронизации пользователей Kaiten.
     */
    private final KaitenUserSyncService userSyncService;

    /**
     * Сервис для сбора карточек Kaiten.
     */
    private final KaitenCardCollectorService cardCollectorService;

    /**
     * Запускает синхронизацию всех пользователей из Kaiten.
     * 
     * @return сообщение о запуске синхронизации
     */
    @PostMapping("/sync/users")
    public ResponseEntity<String> syncUsers() {
        userSyncService.syncAllUsers();
        return ResponseEntity.ok("Users sync started");
    }

    /**
     * Запускает сбор карточек из всех пространств Kaiten.
     * 
     * <p>Если параметр since не указан, собираются карточки за последние 30 дней.</p>
     * 
     * @param since дата и время начала сбора карточек (опционально)
     * @return сообщение о запуске сбора карточек
     */
    @PostMapping("/sync/cards")
    public ResponseEntity<String> syncCards(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since
    ) {
        LocalDateTime syncSince = since != null ? since : LocalDateTime.now().minusDays(30);
        cardCollectorService.collectCardsFromAllSpaces(syncSince);
        return ResponseEntity.ok("Cards collection started");
    }

    /**
     * Запускает сбор карточек для указанной команды.
     * 
     * <p>Если параметр since не указан в запросе, собираются карточки за последние 30 дней.</p>
     * 
     * @param request запрос с email-адресами команды и датой начала сбора
     * @return сообщение о запуске сбора карточек команды
     */
    @PostMapping("/sync/team")
    public ResponseEntity<String> syncTeamCards(@RequestBody KaitenSyncRequest request) {
        LocalDateTime syncSince = request.getSince() != null ? request.getSince() : LocalDateTime.now().minusDays(30);
        cardCollectorService.collectCardsForTeam(request.getTeamEmails(), syncSince);
        return ResponseEntity.ok("Team cards collection started");
    }
}
