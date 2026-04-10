package ru.x5.markable.dev.analytics.gitlab.rest.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.AiSummaryDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.UserProfileDto;
import ru.x5.markable.dev.analytics.gitlab.service.AiSummaryService;
import ru.x5.markable.dev.analytics.gitlab.service.UserProfileService;

/**
 * REST-контроллер для генерации AI-сводок по профилям пользователей.
 * 
 * <p>Предоставляет API для получения AI-сгенерированных сводок
 * на основе статистики активности пользователя.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Log4j2
public class AiSummaryController {

    /**
     * Сервис для работы с профилями пользователей.
     */
    private final UserProfileService userProfileService;

    /**
     * Сервис для генерации AI-сводок.
     */
    private final AiSummaryService aiSummaryService;

    /**
     * Получает AI-сводку по профилю пользователя.
     * 
     * <p>Метод сначала получает профиль пользователя, затем генерирует
     * AI-сводку на основе его статистики активности.</p>
     * 
     * @param email email пользователя
     * @return AI-сводка или 404, если пользователь не найден
     */
    @GetMapping("/{email}/summary")
    public ResponseEntity<AiSummaryDto> getUserSummary(@PathVariable String email) {
        log.info("GET /api/v1/users/{}/summary", email);

        UserProfileDto profile = userProfileService.getUserProfile(email);

        if (profile == null) {
            return ResponseEntity.notFound().build();
        }

        AiSummaryDto summary = aiSummaryService.generateSummary(profile);

        return ResponseEntity.ok(summary);
    }
}
