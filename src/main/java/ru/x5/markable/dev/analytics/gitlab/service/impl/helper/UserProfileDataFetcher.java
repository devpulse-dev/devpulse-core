package ru.x5.markable.dev.analytics.gitlab.service.impl.helper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.DailyAuthorStats;
import ru.x5.markable.dev.analytics.gitlab.persistence.repository.DailyAuthorStatsRepository;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.CommitDetailDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.TaskWithCommitsDto;
import ru.x5.markable.dev.analytics.gitlab.service.CommitDetailsService;

/**
 * Вспомогательный класс для получения данных профиля пользователя.
 * Отвечает за извлечение статистики, коммитов и задач из репозиториев.
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class UserProfileDataFetcher {

    private final DailyAuthorStatsRepository dailyStatsRepository;
    private final CommitDetailsService commitDetailsService;

    /**
     * Получает статистику пользователя за указанный период.
     *
     * @param email email пользователя
     * @param periodStart начало периода (может быть null)
     * @param periodEnd конец периода (может быть null)
     * @return список статистических записей
     */
    public List<DailyAuthorStats> fetchUserStats(String email, LocalDate periodStart, LocalDate periodEnd) {
        return Optional.ofNullable(periodStart)
                .flatMap(start -> Optional.ofNullable(periodEnd)
                        .map(end -> dailyStatsRepository.findByEmailAndDateBetween(email, start, end)))
                .orElseGet(() -> dailyStatsRepository.findByEmailOrderByDateAsc(email));
    }

    /**
     * Получает почасовую активность пользователя за указанный период.
     *
     * @param email email пользователя
     * @param periodStart начало периода (может быть null)
     * @param periodEnd конец периода (может быть null)
     * @return карта активности по часам
     */
    public java.util.Map<Integer, Long> fetchHourlyActivity(String email, LocalDate periodStart, LocalDate periodEnd) {
        return Optional.ofNullable(periodStart)
                .flatMap(start -> Optional.ofNullable(periodEnd)
                        .map(end -> commitDetailsService.getHourlyActivity(email, start, end)))
                .orElseGet(() -> commitDetailsService.getHourlyActivity(email));
    }

    /**
     * Получает задачи пользователя с коммитами за указанный период.
     *
     * @param email email пользователя
     * @param periodStart начало периода (может быть null)
     * @param periodEnd конец периода (может быть null)
     * @return список задач с коммитами
     */
    public List<TaskWithCommitsDto> fetchTasks(String email, LocalDate periodStart, LocalDate periodEnd) {
        return Optional.ofNullable(periodStart)
                .flatMap(start -> Optional.ofNullable(periodEnd)
                        .map(end -> commitDetailsService.getTasksWithCommits(email, start, end)))
                .orElseGet(() -> commitDetailsService.getTasksWithCommits(email));
    }

    /**
     * Получает список репозиториев, в которых работал пользователь.
     *
     * @param email email пользователя
     * @param periodStart начало периода (может быть null)
     * @param periodEnd конец периода (может быть null)
     * @return множество имён репозиториев
     */
    public Set<String> fetchRepositories(String email, LocalDate periodStart, LocalDate periodEnd) {
        return dailyStatsRepository.findRepositoriesByEmailAndPeriod(email, periodStart, periodEnd).stream()
                .filter(repo -> !"ALL_REPOS".equals(repo))
                .collect(Collectors.toSet());
    }

    /**
     * Получает коммиты пользователя за указанный период.
     *
     * @param email email пользователя
     * @param periodStart начало периода (может быть null)
     * @param periodEnd конец периода (может быть null)
     * @return список деталей коммитов
     */
    public List<CommitDetailDto> fetchCommits(String email, LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart != null && periodEnd != null) {
            return commitDetailsService.getUserCommits(email, periodStart, periodEnd);
        }
        return commitDetailsService.getUserCommits(email);
    }
}
