package ru.x5.devpulse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.x5.devpulse.adapter.kaiten.KaitenProperties;
import ru.x5.devpulse.application.port.in.CancelCollectionUseCase;
import ru.x5.devpulse.application.port.in.CollectDailyStatsUseCase;
import ru.x5.devpulse.application.port.in.CollectGitStatsUseCase;
import ru.x5.devpulse.application.port.in.CollectReviewsUseCase;
import ru.x5.devpulse.application.port.in.SetTeamLeadUseCase;
import ru.x5.devpulse.application.port.in.SetUserTeamUseCase;
import ru.x5.devpulse.application.port.in.SyncKaitenUsersUseCase;
import ru.x5.devpulse.application.port.out.CollectionLock;
import ru.x5.devpulse.application.port.out.CollectionRunRepository;
import ru.x5.devpulse.application.port.out.CommitRepository;
import ru.x5.devpulse.application.port.out.DailyStatsRepository;
import ru.x5.devpulse.application.port.out.GitGateway;
import ru.x5.devpulse.application.port.out.KaitenCardsCache;
import ru.x5.devpulse.application.port.out.KaitenGateway;
import ru.x5.devpulse.application.port.out.ReviewGateway;
import ru.x5.devpulse.application.port.out.ReviewWriteRepository;
import ru.x5.devpulse.application.port.out.TransactionRunner;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.application.service.CancelCollectionService;
import ru.x5.devpulse.application.service.CollectDailyStatsService;
import ru.x5.devpulse.application.service.CollectGitStatsService;
import ru.x5.devpulse.application.service.CollectReviewsService;
import ru.x5.devpulse.application.service.SetTeamLeadService;
import ru.x5.devpulse.application.service.SetUserTeamService;
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

    /** Отмена идущего прогона — ставит флаг, сбор остановится на ближайшем checkpoint'е. */
    @Bean
    CancelCollectionUseCase cancelCollectionUseCase(CollectionRunRepository collectionRunRepository) {
        return new CancelCollectionService(collectionRunRepository);
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

    /** Worker — kaiten users sync (резолв непривязанных + рефреш привязанных в unified_user). */
    @Bean
    SyncKaitenUsersUseCase syncKaitenUsersUseCase(
            KaitenGateway kaitenGateway,
            UnifiedUserRepository unifiedUserRepository,
            KaitenProperties kaitenProperties) {
        return new SyncKaitenUsersService(
                kaitenGateway, unifiedUserRepository, kaitenProperties.userRefreshInterval());
    }

    /** Назначение/снятие команды пользователя (управление командами с фронта). */
    @Bean
    SetUserTeamUseCase setUserTeamUseCase(UnifiedUserRepository unifiedUserRepository) {
        return new SetUserTeamService(unifiedUserRepository);
    }

    /** Назначение/снятие лида команды (один лид на команду). */
    @Bean
    SetTeamLeadUseCase setTeamLeadUseCase(UnifiedUserRepository unifiedUserRepository) {
        return new SetTeamLeadService(unifiedUserRepository);
    }
}
