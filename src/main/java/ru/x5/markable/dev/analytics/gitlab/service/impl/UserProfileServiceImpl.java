package ru.x5.markable.dev.analytics.gitlab.service.impl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.DailyAuthorStats;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.UnifiedUser;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.CommitDetailDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.KaitenCardWithCommitsDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.UserProfileDto;
import ru.x5.markable.dev.analytics.gitlab.service.CommitDetailsService;
import ru.x5.markable.dev.analytics.gitlab.service.UnifiedUserService;
import ru.x5.markable.dev.analytics.gitlab.service.UserProfileService;
import ru.x5.markable.dev.analytics.gitlab.service.impl.helper.KaitenCardFetcher;
import ru.x5.markable.dev.analytics.gitlab.service.impl.helper.UserProfileDataFetcher;
import ru.x5.markable.dev.analytics.gitlab.service.impl.helper.UserProfileSummaryGenerator;

/**
 * Сервис для работы с профилями пользователей.
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Получение профиля пользователя с агрегированной статистикой</li>
 *   <li>Расчёт метрик активности (по дням, часам, периодам)</li>
 *   <li>Интеграция с Kaiten для получения карточек задач</li>
 *   <li>Генерация текстовой сводки о деятельности пользователя</li>
 * </ul>
 * 
 * <p>Сервис использует вспомогательные классы для разделения ответственности:</p>
 * <ul>
 *   <li>{@link UserProfileDataFetcher} - получение данных из репозиториев</li>
 *   <li>{@link KaitenCardFetcher} - получение карточек Kaiten</li>
 *   <li>{@link UserProfileSummaryGenerator} - расчёт метрик и генерация сводки</li>
 * </ul>
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final CommitDetailsService commitDetailsService;
    private final UnifiedUserService unifiedUserService;
    private final UserProfileDataFetcher dataFetcher;
    private final KaitenCardFetcher kaitenCardFetcher;
    private final UserProfileSummaryGenerator summaryGenerator;

    @Override
    public UserProfileDto getUserProfile(String email) {
        return getUserProfile(email, null, null);
    }

    /**
     * Получает профиль пользователя с агрегированной статистикой.
     *
     * @param email email пользователя
     * @param periodStart начало периода (может быть null)
     * @param periodEnd конец периода (может быть null)
     * @return DTO профиля пользователя или null если данных нет
     */
    @Override
    public UserProfileDto getUserProfile(String email, LocalDate periodStart, LocalDate periodEnd) {
        log.info("Fetching profile for user: {} with period: {} - {}", email, periodStart, periodEnd);

        Optional<UnifiedUser> unifiedUser = unifiedUserService.findByEmail(email);

        List<DailyAuthorStats> userStats = dataFetcher.fetchUserStats(email, periodStart, periodEnd);

        if (userStats.isEmpty()) {
            log.warn("No data found for user: {}", email);
            return null;
        }

        var aggregatedStats = summaryGenerator.aggregateStats(userStats);
        var activityByDay = summaryGenerator.calculateActivityByDay(userStats);
        var inactivePeriods = summaryGenerator.calculateInactivePeriods(userStats);
        var repositories = dataFetcher.fetchRepositories(email, periodStart, periodEnd);
        var activityByHour = dataFetcher.fetchHourlyActivity(email, periodStart, periodEnd);
        var tasks = dataFetcher.fetchTasks(email, periodStart, periodEnd);
        var kaitenCards = kaitenCardFetcher.fetchKaitenCards(
                unifiedUser.map(UnifiedUser::getKaitenId).orElse(null),
                tasks,
                periodStart,
                periodEnd
        );

        LocalDate firstDate = userStats.get(0).getDate();
        LocalDate lastDate = userStats.get(userStats.size() - 1).getDate();
        long activeDays = activityByDay.values().stream().filter(v -> v != 0).count();
        long totalDays = ChronoUnit.DAYS.between(periodStart, periodEnd) - 1;
        double avgCommitsPerDay = (double) aggregatedStats.totalCommits() / activeDays;

        String aiSummary = summaryGenerator.generateSummary(
                email, aggregatedStats.totalCommits(), activeDays, totalDays,
                periodStart, periodEnd,
                activityByDay, activityByHour,
                inactivePeriods,
                repositories,
                tasks
        );

        return UserProfileDto.builder()
                .email(email)
                .username(summaryGenerator.extractUsername(email))
                .joinedDate(firstDate)
                .avatarUrl(unifiedUser.map(UnifiedUser::getAvatarUrl).orElse(null))
                .totalCommits(aggregatedStats.totalCommits())
                .totalMergeCommits(aggregatedStats.totalMergeCommits())
                .totalAddedLines(aggregatedStats.totalAdded())
                .totalDeletedLines(aggregatedStats.totalDeleted())
                .totalTestAddedLines(aggregatedStats.totalTestAdded())
                .activeDays(activeDays)
                .totalDays(totalDays)
                .avgCommitsPerDay(avgCommitsPerDay)
                .periodStart(firstDate)
                .periodEnd(lastDate)
                .activityByDay(activityByDay)
                .activityByHour(activityByHour)
                .repositories(new ArrayList<>(repositories))
                .tasks(tasks)
                .kaitenCards(kaitenCards)
                .inactivePeriods(inactivePeriods)
                .aiSummary(aiSummary)
                .build();
    }

    /**
     * Получает коммиты пользователя.
     *
     * @param email email пользователя
     * @return список деталей коммитов
     */
    @Override
    public List<CommitDetailDto> getUserCommits(String email) {
        log.info("Fetching commits for user: {}", email);
        return commitDetailsService.getUserCommits(email);
    }
}
