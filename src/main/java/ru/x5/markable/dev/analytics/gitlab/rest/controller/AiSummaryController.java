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

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Log4j2
public class AiSummaryController {

    private final UserProfileService userProfileService;
    private final AiSummaryService aiSummaryService;

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
