package ru.x5.markable.dev.analytics.gitlab.rest.controller;

import java.time.LocalDate;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Log4j2
public class UserProfileController {

    private final UserProfileService userProfileService;

    /**
     * Получить профиль пользователя
     * @param email email пользователя
     * @param start дата начала периода (опционально)
     * @param end дата окончания периода (опционально)
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

//    @GetMapping("/{email}")
//    public ResponseEntity<UserProfileDto> getUserProfile(@PathVariable String email) {
//        log.info("GET /api/v1/users/{}", email);
//
//        UserProfileDto profile = userProfileService.getUserProfile(email);
//
//        if (profile == null) {
//            return ResponseEntity.notFound().build();
//        }
//
//        return ResponseEntity.ok(profile);
//    }

    @GetMapping("/{email}/commits")
    public ResponseEntity<List<CommitDetailDto>> getUserCommits(@PathVariable String email) {
        log.info("GET /api/v1/users/{}/commits", email);

        List<CommitDetailDto> commits = userProfileService.getUserCommits(email);

        return ResponseEntity.ok(commits);
    }
}
