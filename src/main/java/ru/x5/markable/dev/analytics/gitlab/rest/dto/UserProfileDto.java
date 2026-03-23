package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {

    // Основная информация
    private String email;
    private String username;
    private LocalDate joinedDate;

    // Общая статистика
    private long totalCommits;
    private long totalMergeCommits;
    private long totalAddedLines;
    private long totalDeletedLines;
    private long totalTestAddedLines;

    // Активность
    private long activeDays;
    private long totalDays;
    private double avgCommitsPerDay;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    // Активность по дням недели (0-6, где 0=понедельник)
    private Map<String, Long> activityByDay;

    // Активность по часам (0-23)
    private Map<Integer, Long> activityByHour;

    // Репозитории
    private List<String> repositories;

    private List<TaskWithCommitsDto> tasks;

    private List<String> inactivePeriods;

    // AI Summary
    private String aiSummary;
}
