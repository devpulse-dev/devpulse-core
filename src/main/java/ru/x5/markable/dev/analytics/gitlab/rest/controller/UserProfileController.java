package ru.x5.markable.dev.analytics.gitlab.rest.controller;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.CommitDetailDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.UserProfileDto;
import ru.x5.markable.dev.analytics.gitlab.service.UserProfileService;

/**
 * REST-контроллер для работы с профилями пользователей.
 * 
 * <p>Предоставляет API для получения профилей пользователей, их статистики
 * и списка коммитов за указанный период.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Log4j2
public class UserProfileController {

    /**
     * Сервис для работы с профилями пользователей.
     */
    private final UserProfileService userProfileService;

    /**
     * Получает профиль пользователя с его статистикой.
     * 
     * <p>Если указаны параметры start и end, возвращается статистика за указанный период.
     * В противном случае возвращается статистика за весь период.</p>
     * 
     * @param email email пользователя
     * @param start дата начала периода (опционально)
     * @param end дата окончания периода (опционально)
     * @return профиль пользователя с статистикой или 404, если пользователь не найден
     */
    @GetMapping("/{email}")
    public ResponseEntity<UserProfileDto> getUserProfile(
            @PathVariable String email,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        log.info("GET /api/v1/users/{} with period: {} - {}", email, start, end);

        UserProfileDto profile;
        if (start != null && end != null) {
            profile = userProfileService.getUserProfile(email, start, end);
        } else {
            profile = userProfileService.getUserProfile(email);
        }

        if (profile == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(profile);
    }

    /**
     * Получает список коммитов пользователя.
     * 
     * @param email email пользователя
     * @return список коммитов пользователя
     */
    @GetMapping("/{email}/commits")
    public ResponseEntity<List<CommitDetailDto>> getUserCommits(@PathVariable String email) {
        log.info("GET /api/v1/users/{}/commits", email);

        List<CommitDetailDto> commits = userProfileService.getUserCommits(email);

        return ResponseEntity.ok(commits);
    }
}
