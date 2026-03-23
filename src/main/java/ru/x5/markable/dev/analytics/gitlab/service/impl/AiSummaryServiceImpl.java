package ru.x5.markable.dev.analytics.gitlab.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ru.x5.markable.dev.analytics.gitlab.client.AiClient;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.AiSummaryDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.UserProfileDto;
import ru.x5.markable.dev.analytics.gitlab.service.AiSummaryService;

import java.util.Map;

@Service
@Log4j2
@RequiredArgsConstructor
public class AiSummaryServiceImpl implements AiSummaryService {

    private final AiClient aiClient;

    @Override
    public AiSummaryDto generateSummary(UserProfileDto profile) {
        log.info("Generating AI summary for user: {}", profile.getEmail());

        long startTime = System.currentTimeMillis();

        String summary = aiClient.generate(profile);

        long generationTime = System.currentTimeMillis() - startTime;

        return AiSummaryDto.builder()
                .summary(summary)
                .generationTimeMs(generationTime)
                .model("corporate-ai")
                .build();
    }
}
