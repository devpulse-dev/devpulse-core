package ru.x5.markable.dev.analytics.kaiten.rest.controller;

import java.time.LocalDateTime;
import java.util.List;
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

/**
 * REST-контроллер для получения статистики по карточкам Kaiten.
 * 
 * <p>Предоставляет API для получения карточек Kaiten и общей статистики
 * по ним, включая фильтрацию по диапазону дат.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/v1/kaiten")
@RequiredArgsConstructor
@Log4j2
public class KaitenStatsController {

    /**
     * Сервис для работы со статистикой Kaiten.
     */
    private final KaitenStatsService statsService;

    /**
     * Получает все карточки Kaiten.
     * 
     * @return список всех карточек
     */
    @GetMapping("/cards")
    public ResponseEntity<List<KaitenCardResponseDto>> getAllCards() {
        log.info("GET /api/v1/kaiten/cards");
        return ResponseEntity.ok(statsService.getAllCards());
    }

    /**
     * Получает карточки Kaiten за указанный период.
     * 
     * @param from дата и время начала периода
     * @param to дата и время окончания периода
     * @return список карточек за указанный период
     */
    @GetMapping("/cards/range")
    public ResponseEntity<List<KaitenCardResponseDto>> getCardsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        log.info("GET /api/v1/kaiten/cards/range?from={}&to={}", from, to);
        return ResponseEntity.ok(statsService.getCardsByDateRange(from, to));
    }

    /**
     * Получает общую статистику по карточкам Kaiten.
     * 
     * @return статистика по карточкам
     */
    @GetMapping("/stats")
    public ResponseEntity<KaitenStatsResponseDto> getStats() {
        log.info("GET /api/v1/kaiten/stats");
        return ResponseEntity.ok(statsService.getStats());
    }
}
