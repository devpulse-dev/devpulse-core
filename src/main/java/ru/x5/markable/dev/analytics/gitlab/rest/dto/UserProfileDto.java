package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.Map;

/**
 * DTO профиля пользователя.
 * 
 * <p>Содержит полную информацию о профиле пользователя, включая основную информацию,
 * статистику коммитов, активность, репозитории, задачи и карточки Kaiten.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {

    // Основная информация
    
    /**
     * Email пользователя.
     * 
     * <p>Email адрес пользователя.</p>
     */
    private String email;
    
    /**
     * Имя пользователя.
     * 
     * <p>Имя пользователя в системе.</p>
     */
    private String username;
    
    /**
     * Дата присоединения.
     * 
     * <p>Дата регистрации пользователя в системе.</p>
     */
    private LocalDate joinedDate;
    
    /**
     * URL аватара.
     * 
     * <p>Ссылка на аватар пользователя.</p>
     */
    private String avatarUrl;

    // Общая статистика
    
    /**
     * Общее количество коммитов.
     * 
     * <p>Общее количество коммитов, сделанных пользователем.</p>
     */
    private long totalCommits;
    
    /**
     * Общее количество merge-коммитов.
     * 
     * <p>Количество коммитов слияния, сделанных пользователем.</p>
     */
    private long totalMergeCommits;
    
    /**
     * Общее количество добавленных строк.
     * 
     * <p>Общее количество строк кода, добавленных пользователем.</p>
     */
    private long totalAddedLines;
    
    /**
     * Общее количество удалённых строк.
     * 
     * <p>Общее количество строк кода, удалённых пользователем.</p>
     */
    private long totalDeletedLines;
    
    /**
     * Общее количество добавленных строк в тестах.
     * 
     * <p>Количество строк кода, добавленных в тестовых файлах.</p>
     */
    private long totalTestAddedLines;

    // Активность
    
    /**
     * Количество активных дней.
     * 
     * <p>Количество дней, в которые пользователь делал коммиты.</p>
     */
    private long activeDays;
    
    /**
     * Общее количество дней.
     * 
     * <p>Общее количество дней в периоде анализа.</p>
     */
    private long totalDays;
    
    /**
     * Среднее количество коммитов в день.
     * 
     * <p>Среднее количество коммитов, сделанных пользователем в день.</p>
     */
    private double avgCommitsPerDay;
    
    /**
     * Начало периода.
     * 
     * <p>Дата начала периода анализа.</p>
     */
    private LocalDate periodStart;
    
    /**
     * Конец периода.
     * 
     * <p>Дата окончания периода анализа.</p>
     */
    private LocalDate periodEnd;

    // Активность по дням недели
    
    /**
     * Активность по дням недели.
     * 
     * <p>Карта активности пользователя по дням недели.</p>
     */
    private Map<String, Long> activityByDay;

    // Активность по часам (0-23)
    
    /**
     * Активность по часам.
     * 
     * <p>Карта активности пользователя по часам дня (0-23).</p>
     */
    private Map<Integer, Long> activityByHour;

    // Репозитории
    
    /**
     * Список репозиториев.
     * 
     * <p>Список репозиториев, в которых пользователь делал коммиты.</p>
     */
    private List<String> repositories;

    /**
     * Список задач.
     * 
     * <p>Список задач с коммитами пользователя.</p>
     */
    private List<TaskWithCommitsDto> tasks;

    /**
     * Список неактивных периодов.
     * 
     * <p>Список периодов, когда пользователь не делал коммиты.</p>
     */
    private List<String> inactivePeriods;

    // AI Summary
    
    /**
     * AI-сводка.
     * 
     * <p>Сводка о пользователе, сгенерированная искусственным интеллектом.</p>
     */
    private String aiSummary;

    /**
     * Список карточек Kaiten.
     * 
     * <p>Список карточек Kaiten с коммитами пользователя.</p>
     */
    private List<KaitenCardWithCommitsDto> kaitenCards;
}