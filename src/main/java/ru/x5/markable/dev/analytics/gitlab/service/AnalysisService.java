package ru.x5.markable.dev.analytics.gitlab.service;

import java.util.List;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.AuthorStats;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.AnalysisRequest;

/**
 * Сервис для анализа Git-репозиториев.
 * 
 * <p>Предоставляет функциональность для запуска анализа репозиториев,
 * сбора статистики по авторам и коммитам.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
public interface AnalysisService {

    /**
     * Запускает анализ Git-репозиториев.
     * 
     * <p>Выполняет анализ указанных репозиториев, собирает информацию о коммитах,
     * авторах и других метриках. Результаты анализа сохраняются в базе данных.</p>
     * 
     * @param request запрос на анализ с параметрами репозиториев и периодом
     * @return список статистики по авторам
     */
    List<AuthorStats> startAnalysis(AnalysisRequest request);

}
