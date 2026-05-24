package ru.x5.markable.dev.analytics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.x5.markable.dev.analytics.application.port.in.CollectDailyStatsUseCase;
import ru.x5.markable.dev.analytics.application.port.in.GetCollectionRunUseCase;
import ru.x5.markable.dev.analytics.application.port.in.GetDailyStatsUseCase;
import ru.x5.markable.dev.analytics.application.port.in.GetDashboardUseCase;
import ru.x5.markable.dev.analytics.application.port.in.GetPeriodSummaryUseCase;
import ru.x5.markable.dev.analytics.application.port.in.GetUserCommitsUseCase;
import ru.x5.markable.dev.analytics.application.port.in.GetUserProfileUseCase;
import ru.x5.markable.dev.analytics.application.port.in.GetWeeklyStatsUseCase;
import ru.x5.markable.dev.analytics.application.port.in.SyncKaitenUsersUseCase;
import ru.x5.markable.dev.analytics.application.port.out.CollectionRunRepository;
import ru.x5.markable.dev.analytics.application.port.out.CommitRepository;
import ru.x5.markable.dev.analytics.application.port.out.DailyStatsRepository;
import ru.x5.markable.dev.analytics.application.port.out.GitGateway;
import ru.x5.markable.dev.analytics.application.port.out.KaitenGateway;
import ru.x5.markable.dev.analytics.application.port.out.KaitenUserRepository;
import ru.x5.markable.dev.analytics.application.port.out.UnifiedUserRepository;
import ru.x5.markable.dev.analytics.application.service.CollectDailyStatsService;
import ru.x5.markable.dev.analytics.application.service.GetCollectionRunService;
import ru.x5.markable.dev.analytics.application.service.GetDailyStatsService;
import ru.x5.markable.dev.analytics.application.service.GetDashboardService;
import ru.x5.markable.dev.analytics.application.service.GetPeriodSummaryService;
import ru.x5.markable.dev.analytics.application.service.GetUserCommitsService;
import ru.x5.markable.dev.analytics.application.service.GetUserProfileService;
import ru.x5.markable.dev.analytics.application.service.GetWeeklyStatsService;
import ru.x5.markable.dev.analytics.application.service.SyncKaitenUsersService;

/**
 * Wiring use case-ов из портов.
 *
 * <p>Use case-ы живут в {@code application} как чистые POJO (без Spring) —
 * поэтому регистрируем их явно здесь, в bootstrap.</p>
 *
 * <p>Это намеренное решение: application-модуль НЕ зависит от Spring
 * (enforced ArchUnit-тестом {@code HexagonalArchitectureTest}). Любой новый
 * use case добавляется как {@code @Bean} в этот класс.</p>
 */
@Configuration
public class UseCaseConfig {

    /* ---------- command-side ---------- */

    @Bean
    CollectDailyStatsUseCase collectDailyStatsUseCase(
            GitGateway gitGateway,
            KaitenGateway kaitenGateway,
            CommitRepository commitRepository,
            DailyStatsRepository dailyStatsRepository,
            KaitenUserRepository kaitenUserRepository,
            UnifiedUserRepository unifiedUserRepository,
            CollectionRunRepository collectionRunRepository) {
        return new CollectDailyStatsService(
                gitGateway, kaitenGateway,
                commitRepository, dailyStatsRepository, kaitenUserRepository,
                unifiedUserRepository, collectionRunRepository);
    }

    @Bean
    SyncKaitenUsersUseCase syncKaitenUsersUseCase(
            KaitenGateway kaitenGateway,
            KaitenUserRepository kaitenUserRepository) {
        return new SyncKaitenUsersService(kaitenGateway, kaitenUserRepository);
    }

    /* ---------- query-side ---------- */

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
            UnifiedUserRepository unifiedUserRepository) {
        return new GetDashboardService(dailyStatsRepository, unifiedUserRepository);
    }
}
