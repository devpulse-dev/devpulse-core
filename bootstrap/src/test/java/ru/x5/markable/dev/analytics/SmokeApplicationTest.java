package ru.x5.markable.dev.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.x5.markable.dev.analytics.application.port.in.CollectDailyStatsUseCase;
import ru.x5.markable.dev.analytics.application.port.in.GetCollectionRunUseCase;
import ru.x5.markable.dev.analytics.application.port.in.GetDailyStatsUseCase;
import ru.x5.markable.dev.analytics.application.port.in.GetPeriodSummaryUseCase;
import ru.x5.markable.dev.analytics.application.port.in.GetUserCommitsUseCase;
import ru.x5.markable.dev.analytics.application.port.in.GetUserProfileUseCase;
import ru.x5.markable.dev.analytics.application.port.in.GetWeeklyStatsUseCase;
import ru.x5.markable.dev.analytics.application.port.in.SyncKaitenUsersUseCase;
import ru.x5.markable.dev.analytics.application.port.out.CollectionRunRepository;
import ru.x5.markable.dev.analytics.application.port.out.CommitRepository;
import ru.x5.markable.dev.analytics.application.port.out.DailyStatsRepository;
import ru.x5.markable.dev.analytics.application.port.out.GitGateway;
import ru.x5.markable.dev.analytics.application.port.out.KaitenCardRepository;
import ru.x5.markable.dev.analytics.application.port.out.KaitenGateway;
import ru.x5.markable.dev.analytics.application.port.out.KaitenUserRepository;
import ru.x5.markable.dev.analytics.application.port.out.UnifiedUserRepository;

/**
 * Smoke-тест: поднимает полный Spring-контекст с реальной БД (Testcontainers),
 * проверяет что все use case-ы и порты собраны в граф.
 *
 * <p>Реальные HTTP/Git вызовы НЕ выполняются — внешние сервисы не настроены,
 * проверка идёт только на сборку контекста.</p>
 */
@SpringBootTest(classes = Application.class)
@Testcontainers
@DisplayName("Smoke: Spring-контекст поднимается, граф полный")
class SmokeApplicationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("devanalytics_smoke")
            .withUsername("test")
            .withPassword("test");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired ApplicationContext ctx;

    // command-side
    @Autowired CollectDailyStatsUseCase collectDailyStats;
    @Autowired SyncKaitenUsersUseCase syncKaitenUsers;
    // query-side
    @Autowired GetDailyStatsUseCase getDailyStats;
    @Autowired GetWeeklyStatsUseCase getWeeklyStats;
    @Autowired GetPeriodSummaryUseCase getPeriodSummary;
    @Autowired GetUserProfileUseCase getUserProfile;
    @Autowired GetUserCommitsUseCase getUserCommits;
    @Autowired GetCollectionRunUseCase getCollectionRun;
    // out-ports
    @Autowired GitGateway gitGateway;
    @Autowired KaitenGateway kaitenGateway;
    @Autowired CommitRepository commitRepository;
    @Autowired DailyStatsRepository dailyStatsRepository;
    @Autowired KaitenCardRepository kaitenCardRepository;
    @Autowired KaitenUserRepository kaitenUserRepository;
    @Autowired UnifiedUserRepository unifiedUserRepository;
    @Autowired CollectionRunRepository collectionRunRepository;

    @Test
    @DisplayName("Все use case-ы и порты собраны, контекст консистентен")
    void contextLoads() {
        assertAll("граф собран",
                () -> assertThat(ctx).isNotNull(),
                // command
                () -> assertThat(collectDailyStats).as("CollectDailyStats").isNotNull(),
                () -> assertThat(syncKaitenUsers).as("SyncKaitenUsers").isNotNull(),
                // query
                () -> assertThat(getDailyStats).as("GetDailyStats").isNotNull(),
                () -> assertThat(getWeeklyStats).as("GetWeeklyStats").isNotNull(),
                () -> assertThat(getPeriodSummary).as("GetPeriodSummary").isNotNull(),
                () -> assertThat(getUserProfile).as("GetUserProfile").isNotNull(),
                () -> assertThat(getUserCommits).as("GetUserCommits").isNotNull(),
                () -> assertThat(getCollectionRun).as("GetCollectionRun").isNotNull(),
                // out-ports
                () -> assertThat(gitGateway).as("GitGateway").isNotNull(),
                () -> assertThat(kaitenGateway).as("KaitenGateway").isNotNull(),
                () -> assertThat(commitRepository).as("CommitRepository").isNotNull(),
                () -> assertThat(dailyStatsRepository).as("DailyStatsRepository").isNotNull(),
                () -> assertThat(kaitenCardRepository).as("KaitenCardRepository").isNotNull(),
                () -> assertThat(kaitenUserRepository).as("KaitenUserRepository").isNotNull(),
                () -> assertThat(unifiedUserRepository).as("UnifiedUserRepository").isNotNull(),
                () -> assertThat(collectionRunRepository).as("CollectionRunRepository").isNotNull());
    }
}
