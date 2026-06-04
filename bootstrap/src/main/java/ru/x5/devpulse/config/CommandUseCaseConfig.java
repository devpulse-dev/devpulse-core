package ru.x5.devpulse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.x5.devpulse.application.port.in.CollectDailyStatsUseCase;
import ru.x5.devpulse.application.port.in.CollectGitStatsUseCase;
import ru.x5.devpulse.application.port.in.CollectReviewsUseCase;
import ru.x5.devpulse.application.port.in.SyncKaitenUsersUseCase;
import ru.x5.devpulse.application.port.out.CollectionLock;
import ru.x5.devpulse.application.port.out.CollectionRunRepository;
import ru.x5.devpulse.application.port.out.CommitRepository;
import ru.x5.devpulse.application.port.out.DailyStatsRepository;
import ru.x5.devpulse.application.port.out.GitGateway;
import ru.x5.devpulse.application.port.out.KaitenCardsCache;
import ru.x5.devpulse.application.port.out.KaitenGateway;
import ru.x5.devpulse.application.port.out.KaitenUserRepository;
import ru.x5.devpulse.application.port.out.ReviewGateway;
import ru.x5.devpulse.application.port.out.ReviewWriteRepository;
import ru.x5.devpulse.application.port.out.TransactionRunner;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.application.service.CollectDailyStatsService;
import ru.x5.devpulse.application.service.CollectGitStatsService;
import ru.x5.devpulse.application.service.CollectReviewsService;
import ru.x5.devpulse.application.service.SyncKaitenUsersService;

/**
 * Wiring command-side use case'ов (write/orchestrator/worker).
 *
 * <p>Разделение command/query — следствие review-фикса N6: один большой {@code UseCaseConfig}
 * с 9 bean'ами читается хуже, чем два узких файла по своей зоне ответственности.
 * Тут — три use case'а, которые меняют состояние или дёргают внешние сервисы.</p>
 */
@Configuration
class CommandUseCaseConfig {

    /**
     * Orchestrator — координирует git-фазу и kaiten-фазу. Сам в I/O не лезет.
     * Реализует public-port {@link CollectDailyStatsUseCase} (его дёргает REST).
     */
    @Bean
    CollectDailyStatsUseCase collectDailyStatsUseCase(
            CollectGitStatsUseCase collectGitStats,
            SyncKaitenUsersUseCase syncKaitenUsers,
            CollectReviewsUseCase collectReviews,
            CollectionRunRepository collectionRunRepository,
            CollectionLock collectionLock,
            KaitenCardsCache kaitenCardsCache) {
        return new CollectDailyStatsService(
                collectGitStats, syncKaitenUsers, collectReviews, collectionRunRepository,
                collectionLock, kaitenCardsCache);
    }

    /** Worker — фаза сбора ревью-метрик из GitLab (MR/approvals/notes → merge_request/mr_review). */
    @Bean
    CollectReviewsUseCase collectReviewsUseCase(
            ReviewGateway reviewGateway,
            ReviewWriteRepository reviewWriteRepository) {
        return new CollectReviewsService(reviewGateway, reviewWriteRepository);
    }

    /** Worker — только git-фаза. Не имеет понятия про CollectionRun/lock. */
    @Bean
    CollectGitStatsUseCase collectGitStatsUseCase(
            GitGateway gitGateway,
            CommitRepository commitRepository,
            DailyStatsRepository dailyStatsRepository,
            UnifiedUserRepository unifiedUserRepository,
            TransactionRunner transactionRunner) {
        return new CollectGitStatsService(
                gitGateway, commitRepository, dailyStatsRepository,
                unifiedUserRepository, transactionRunner);
    }

    /** Worker — kaiten users sync (upsert kaiten_user + link unified_user.kaiten_id). */
    @Bean
    SyncKaitenUsersUseCase syncKaitenUsersUseCase(
            KaitenGateway kaitenGateway,
            KaitenUserRepository kaitenUserRepository,
            UnifiedUserRepository unifiedUserRepository) {
        return new SyncKaitenUsersService(
                kaitenGateway, kaitenUserRepository, unifiedUserRepository);
    }
}
