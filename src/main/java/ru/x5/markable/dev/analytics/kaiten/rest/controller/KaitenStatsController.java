package ru.x5.markable.dev.analytics.kaiten.rest.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenCardResponseDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenStatsResponseDto;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenStatsService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/kaiten")
@RequiredArgsConstructor
@Log4j2
public class KaitenStatsController {

    private final KaitenStatsService statsService;

    @GetMapping("/cards")
    public ResponseEntity<List<KaitenCardResponseDto>> getAllCards() {
        log.info("GET /api/v1/kaiten/cards");
        return ResponseEntity.ok(statsService.getAllCards());
    }

    @GetMapping("/cards/range")
    public ResponseEntity<List<KaitenCardResponseDto>> getCardsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        log.info("GET /api/v1/kaiten/cards/range?from={}&to={}", from, to);
        return ResponseEntity.ok(statsService.getCardsByDateRange(from, to));
    }

    @GetMapping("/stats")
    public ResponseEntity<KaitenStatsResponseDto> getStats() {
        log.info("GET /api/v1/kaiten/stats");
        return ResponseEntity.ok(statsService.getStats());
    }
}
