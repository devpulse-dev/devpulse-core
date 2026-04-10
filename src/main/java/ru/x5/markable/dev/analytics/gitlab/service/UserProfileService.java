package ru.x5.markable.dev.analytics.gitlab.service;

import java.time.LocalDate;
import java.util.List;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.CommitDetailDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.UserProfileDto;

/**
 * Сервис для работы с профилями пользователей.
 * 
 * <p>Предоставляет функциональность для получения профилей пользователей,
 * включая статистику коммитов, активность по дням недели, задачи и другие метрики.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
public interface UserProfileService {

    /**
     * Получить профиль пользователя за весь период.
     * 
     * <p>Возвращает полный профиль пользователя со всей доступной статистикой,
     * включая коммиты, активность по дням недели, задачи и другие метрики.</p>
     * 
     * @param email email пользователя
     * @return DTO профиля пользователя
     */
    UserProfileDto getUserProfile(String email);

    /**
     * Получить профиль пользователя за указанный период.
     * 
     * <p>Возвращает профиль пользователя со статистикой за указанный период,
     * включая коммиты, активность по дням недели, задачи и другие метрики.</p>
     * 
     * @param email email пользователя
     * @param periodStart начало периода
     * @param periodEnd конец периода
     * @return DTO профиля пользователя за период
     */
    UserProfileDto getUserProfile(String email, LocalDate periodStart, LocalDate periodEnd);

    /**
     * Получить коммиты пользователя.
     * 
     * <p>Возвращает список всех коммитов пользователя.</p>
     * 
     * @param email email пользователя
     * @return список DTO с информацией о коммитах
     */
    List<CommitDetailDto> getUserCommits(String email);
}
