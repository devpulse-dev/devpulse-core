package ru.x5.markable.dev.analytics.gitlab.service;

import java.time.LocalDateTime;
import java.util.List;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.DailyCommitStatsDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.DailyUserStatsDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.PeriodSummaryDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.WeeklyCommitStatsDto;

/**
 * Сервис для работы с ежедневной статистикой коммитов.
 * 
 * <p>Предоставляет функциональность для сбора, хранения и получения
 * ежедневной статистики коммитов, включая агрегацию по дням, неделям и периодам.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
public interface DailyStatsService {

    /**
     * Собирает ежедневную статистику коммитов.
     * 
     * <p>Анализирует все коммиты в репозиториях и агрегирует статистику
     * по дням и авторам. Результаты сохраняются в базе данных.</p>
     */
    void collectDailyStats();

    /**
     * Собирает статистику за указанный период.
     * 
     * <p>Анализирует коммиты за указанный период и агрегирует статистику
     * по дням и авторам. Результаты сохраняются в базе данных.</p>
     * 
     * @param start начало периода
     * @param end конец периода
     */
    void collectStatsForPeriod(LocalDateTime start, LocalDateTime end);

    /**
     * Получает всю ежедневную статистику коммитов.
     * 
     * <p>Возвращает список статистики коммитов, сгруппированной по дням.</p>
     * 
     * @return список DTO с ежедневной статистикой коммитов
     */
    List<DailyCommitStatsDto> getAllDailyCommits();

    /**
     * Получает всю ежедневную статистику пользователей.
     * 
     * <p>Возвращает список статистики пользователей, сгруппированной по дням.</p>
     * 
     * @return список DTO с ежедневной статистикой пользователей
     */
    List<DailyUserStatsDto> getAllDailyUserStats();

    /**
     * Получает сводку за период.
     * 
     * <p>Возвращает агрегированную статистику за весь период наблюдения,
     * включая общее количество коммитов, авторов и другие метрики.</p>
     * 
     * @return DTO со сводкой за период
     */
    PeriodSummaryDto getPeriodSummary();

    /**
     * Получает еженедельную статистику коммитов.
     * 
     * <p>Возвращает список статистики коммитов, сгруппированной по неделям.</p>
     * 
     * @return список DTO с еженедельной статистикой коммитов
     */
    List<WeeklyCommitStatsDto> getWeeklyCommits();

}
