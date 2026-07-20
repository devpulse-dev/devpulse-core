package ru.x5.devpulse.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.x5.devpulse.application.port.in.GetCohortsUseCase;
import ru.x5.devpulse.application.port.in.GetCollectionRunUseCase;
import ru.x5.devpulse.application.port.in.GetDailyStatsUseCase;
import ru.x5.devpulse.application.port.in.GetDashboardUseCase;
import ru.x5.devpulse.application.port.in.GetHourlyStatsUseCase;
import ru.x5.devpulse.application.port.in.GetMergedMrStatsUseCase;
import ru.x5.devpulse.application.port.in.GetPerformanceReviewUseCase;
import ru.x5.devpulse.application.port.in.GetPeriodSummaryUseCase;
import ru.x5.devpulse.application.port.in.GetReviewStatsUseCase;
import ru.x5.devpulse.application.port.in.GetTeamDefectsUseCase;
import ru.x5.devpulse.application.port.in.GetUserCommitsUseCase;
import ru.x5.devpulse.application.port.in.GetUserProfileUseCase;
import ru.x5.devpulse.application.port.in.GetWeeklyStatsUseCase;
import ru.x5.devpulse.application.port.in.ListTeamsUseCase;
import ru.x5.devpulse.application.port.in.ListUsersUseCase;
import ru.x5.devpulse.application.port.out.CollectionRunRepository;
import ru.x5.devpulse.application.port.out.CommitRepository;
import ru.x5.devpulse.application.port.out.DailyStatsRepository;
import ru.x5.devpulse.application.port.out.KaitenGateway;
import ru.x5.devpulse.application.port.out.ReviewStatsRepository;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.application.service.GetCohortsService;
import ru.x5.devpulse.application.service.GetCollectionRunService;
import ru.x5.devpulse.application.service.GetDailyStatsService;
import ru.x5.devpulse.application.service.GetDashboardService;
import ru.x5.devpulse.application.service.GetHourlyStatsService;
import ru.x5.devpulse.application.service.GetMergedMrStatsService;
import ru.x5.devpulse.application.service.GetPeriodSummaryService;
import ru.x5.devpulse.application.service.GetReviewStatsService;
import ru.x5.devpulse.application.service.GetTeamDefectsService;
import ru.x5.devpulse.application.service.GetUserCommitsService;
import ru.x5.devpulse.application.service.GetUserProfileService;
import ru.x5.devpulse.application.service.GetWeeklyStatsService;
import ru.x5.devpulse.application.service.ListTeamsService;
import ru.x5.devpulse.application.service.ListUsersService;
import ru.x5.devpulse.application.service.PerformanceReviewService;

/**
 * Wiring query-side use case'ов (read-only).
 *
 * <p>Все эти use case'ы — pure read, никакого I/O в БД кроме SELECT. Они композиционно
 * чище command-side: меньше зависимостей в среднем, нет lifecycle-логики.</p>
 */
@Configuration
class QueryUseCaseConfig {

    @Bean
    GetCollectionRunUseCase getCollectionRunUseCase(CollectionRunRepository collectionRunRepository) {
        return new GetCollectionRunService(collectionRunRepository);
    }

    @Bean
    GetDailyStatsUseCase getDailyStatsUseCase(DailyStatsRepository dailyStatsRepository) {
        return new GetDailyStatsService(dailyStatsRepository);
    }

    @Bean
    GetWeeklyStatsUseCase getWeeklyStatsUseCase(
            DailyStatsRepository dailyStatsRepository,
            UnifiedUserRepository unifiedUserRepository) {
        return new GetWeeklyStatsService(dailyStatsRepository, unifiedUserRepository);
    }

    @Bean
    GetPeriodSummaryUseCase getPeriodSummaryUseCase(
            DailyStatsRepository dailyStatsRepository,
            UnifiedUserRepository unifiedUserRepository) {
        return new GetPeriodSummaryService(dailyStatsRepository, unifiedUserRepository);
    }

    @Bean
    GetUserCommitsUseCase getUserCommitsUseCase(CommitRepository commitRepository) {
        return new GetUserCommitsService(commitRepository);
    }

    @Bean
    GetHourlyStatsUseCase getHourlyStatsUseCase(CommitRepository commitRepository) {
        return new GetHourlyStatsService(commitRepository);
    }

    @Bean
    GetReviewStatsUseCase getReviewStatsUseCase(
            ReviewStatsRepository reviewStatsRepository,
            UnifiedUserRepository unifiedUserRepository) {
        return new GetReviewStatsService(reviewStatsRepository, unifiedUserRepository);
    }

    @Bean
    GetUserProfileUseCase getUserProfileUseCase(
            UnifiedUserRepository unifiedUserRepository,
            DailyStatsRepository dailyStatsRepository,
            CommitRepository commitRepository,
            KaitenGateway kaitenGateway) {
        return new GetUserProfileService(
                unifiedUserRepository, dailyStatsRepository, commitRepository, kaitenGateway);
    }

    /** Досье к perf-review — композиция git/ревью/карточек по одному человеку. */
    @Bean
    GetPerformanceReviewUseCase getPerformanceReviewUseCase(
            UnifiedUserRepository unifiedUserRepository,
            DailyStatsRepository dailyStatsRepository,
            ReviewStatsRepository reviewStatsRepository,
            KaitenGateway kaitenGateway) {
        return new PerformanceReviewService(
                unifiedUserRepository, dailyStatsRepository, reviewStatsRepository, kaitenGateway);
    }

    /** Дефекты команды по приоритету за периоды — live-стрим карточек Kaiten, дедуп по id. */
    @Bean
    GetTeamDefectsUseCase getTeamDefectsUseCase(
            UnifiedUserRepository unifiedUserRepository,
            KaitenGateway kaitenGateway) {
        return new GetTeamDefectsService(unifiedUserRepository, kaitenGateway);
    }

    /** Вмерженные MR по команде за период (только dev-ветки) — агрегат из БД + обогащение. */
    @Bean
    GetMergedMrStatsUseCase getMergedMrStatsUseCase(
            UnifiedUserRepository unifiedUserRepository,
            ReviewStatsRepository reviewStatsRepository,
            @Value("${merged-mrs.dev-branches:dev,main,development}") List<String> devBranches) {
        return new GetMergedMrStatsService(unifiedUserRepository, reviewStatsRepository, devBranches);
    }

    /** Список пользователей (picker perf-review + управление командами). */
    @Bean
    ListUsersUseCase listUsersUseCase(UnifiedUserRepository unifiedUserRepository) {
        return new ListUsersService(unifiedUserRepository);
    }

    /** Список команд (имя + лид + участники) — для фильтра по командам и «кто откуда». */
    @Bean
    ListTeamsUseCase listTeamsUseCase(UnifiedUserRepository unifiedUserRepository) {
        return new ListTeamsService(unifiedUserRepository);
    }

    @Bean
    GetDashboardUseCase getDashboardUseCase(
            DailyStatsRepository dailyStatsRepository,
            UnifiedUserRepository unifiedUserRepository,
            @Value("${scoring.expected-commits-per-30-days:50}") double expectedCommitsPer30Days) {
        return new GetDashboardService(dailyStatsRepository, unifiedUserRepository,
                expectedCommitsPer30Days);
    }

    /** Когорты/retention — лонгитюд по истории коммитов (tier-transitions через тот же scoring). */
    @Bean
    GetCohortsUseCase getCohortsUseCase(
            DailyStatsRepository dailyStatsRepository,
            UnifiedUserRepository unifiedUserRepository,
            @Value("${scoring.expected-commits-per-30-days:50}") double expectedCommitsPer30Days) {
        return new GetCohortsService(dailyStatsRepository, unifiedUserRepository,
                expectedCommitsPer30Days);
    }
}
