package ru.x5.markable.dev.analytics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.x5.markable.dev.analytics.application.port.in.CollectDailyStatsUseCase;
import ru.x5.markable.dev.analytics.application.port.in.SyncKaitenUsersUseCase;
import ru.x5.markable.dev.analytics.application.port.out.CollectionRunRepository;
import ru.x5.markable.dev.analytics.application.port.out.CommitRepository;
import ru.x5.markable.dev.analytics.application.port.out.DailyStatsRepository;
import ru.x5.markable.dev.analytics.application.port.out.GitGateway;
import ru.x5.markable.dev.analytics.application.port.out.KaitenCardRepository;
import ru.x5.markable.dev.analytics.application.port.out.KaitenGateway;
import ru.x5.markable.dev.analytics.application.port.out.KaitenUserRepository;
import ru.x5.markable.dev.analytics.application.port.out.UnifiedUserRepository;
import ru.x5.markable.dev.analytics.application.service.CollectDailyStatsService;
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

    @Bean
    CollectDailyStatsUseCase collectDailyStatsUseCase(
            GitGateway gitGateway,
            KaitenGateway kaitenGateway,
            CommitRepository commitRepository,
            DailyStatsRepository dailyStatsRepository,
            KaitenCardRepository kaitenCardRepository,
            UnifiedUserRepository unifiedUserRepository,
            CollectionRunRepository collectionRunRepository) {
        return new CollectDailyStatsService(
                gitGateway, kaitenGateway,
                commitRepository, dailyStatsRepository, kaitenCardRepository,
                unifiedUserRepository, collectionRunRepository);
    }

    @Bean
    SyncKaitenUsersUseCase syncKaitenUsersUseCase(
            KaitenGateway kaitenGateway,
            KaitenUserRepository kaitenUserRepository) {
        return new SyncKaitenUsersService(kaitenGateway, kaitenUserRepository);
    }
}
