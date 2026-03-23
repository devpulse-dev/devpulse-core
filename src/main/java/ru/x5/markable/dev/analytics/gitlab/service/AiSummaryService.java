package ru.x5.markable.dev.analytics.gitlab.service;

import ru.x5.markable.dev.analytics.gitlab.rest.dto.AiSummaryDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.UserProfileDto;

public interface AiSummaryService {

    /**
     * Генерация AI саммари на основе профиля пользователя
     */
    AiSummaryDto generateSummary(UserProfileDto profile);

}
