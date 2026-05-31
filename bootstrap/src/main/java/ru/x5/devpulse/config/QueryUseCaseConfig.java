package ru.x5.devpulse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.x5.devpulse.application.port.in.GetCollectionRunUseCase;
import ru.x5.devpulse.application.port.in.GetDailyStatsUseCase;
import ru.x5.devpulse.application.port.in.GetDashboardUseCase;
import ru.x5.devpulse.application.port.in.GetHourlyStatsUseCase;
import ru.x5.devpulse.application.port.in.GetPeriodSummaryUseCase;
import ru.x5.devpulse.application.port.in.GetUserCommitsUseCase;
import ru.x5.devpulse.application.port.in.GetUserProfileUseCase;
import ru.x5.devpulse.application.port.in.GetWeeklyStatsUseCase;
import ru.x5.devpulse.application.port.out.CollectionRunRepository;
import ru.x5.devpulse.application.port.out.CommitRepository;
import ru.x5.devpulse.application.port.out.DailyStatsRepository;
import ru.x5.devpulse.application.port.out.KaitenGateway;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.application.service.GetCollectionRunService;
import ru.x5.devpulse.application.service.GetDailyStatsService;
import ru.x5.devpulse.application.service.GetDashboardService;
import ru.x5.devpulse.application.service.GetHourlyStatsService;
import ru.x5.devpulse.application.service.GetPeriodSummaryService;
import ru.x5.devpulse.application.service.GetUserCommitsService;
import ru.x5.devpulse.application.service.GetUserProfileService;
import ru.x5.devpulse.application.service.GetWeeklyStatsService;

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
    GetUserProfileUseCase getUserProfileUseCase(
            UnifiedUserRepository unifiedUserRepository,
            DailyStatsRepository dailyStatsRepository,
            CommitRepository commitRepository,
            KaitenGateway kaitenGateway) {
        return new GetUserProfileService(
                unifiedUserRepository, dailyStatsRepository, commitRepository, kaitenGateway);
    }

    @Bean
    GetDashboardUseCase getDashboardUseCase(
            DailyStatsRepository dailyStatsRepository,
            UnifiedUserRepository unifiedUserRepository,
            @Value("${scoring.expected-commits-per-30-days:50}") double expectedCommitsPer30Days) {
        return new GetDashboardService(dailyStatsRepository, unifiedUserRepository,
                expectedCommitsPer30Days);
    }
}
