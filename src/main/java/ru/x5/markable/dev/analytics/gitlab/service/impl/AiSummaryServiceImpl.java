package ru.x5.markable.dev.analytics.gitlab.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ru.x5.markable.dev.analytics.gitlab.client.AiClient;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.AiSummaryDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.UserProfileDto;
import ru.x5.markable.dev.analytics.gitlab.service.AiSummaryService;

import java.util.Map;

/**
 * Сервис для генерации AI-сводок на основе профилей пользователей.
 * 
 * <p>Использует AI-клиент для генерации текстовых сводок о деятельности разработчиков
 * на основе их статистики коммитов, задач и другой информации.</p>
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Генерация AI-сводок для профилей пользователей</li>
 *   <li>Измерение времени генерации</li>
 *   <li>Возврат информации о модели AI</li>
 * </ul>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see AiSummaryService
 * @see AiClient
 * @see AiSummaryDto
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class AiSummaryServiceImpl implements AiSummaryService {

    private final AiClient aiClient;

    /**
     * Генерирует AI-сводку на основе профиля пользователя.
     * 
     * <p>Отправляет профиль пользователя в AI-клиент для генерации текстовой сводки,
     * измеряет время генерации и возвращает результат с метаданными.</p>
     * 
     * @param profile профиль пользователя с статистикой
     * @return DTO с AI-сводкой, временем генерации и названием модели
     */
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
