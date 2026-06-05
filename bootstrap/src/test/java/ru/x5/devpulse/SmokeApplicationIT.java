package ru.x5.devpulse;

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
import ru.x5.devpulse.application.port.in.CollectDailyStatsUseCase;
import ru.x5.devpulse.application.port.in.GetCollectionRunUseCase;
import ru.x5.devpulse.application.port.in.GetDailyStatsUseCase;
import ru.x5.devpulse.application.port.in.GetDashboardUseCase;
import ru.x5.devpulse.application.port.in.GetPeriodSummaryUseCase;
import ru.x5.devpulse.application.port.in.GetUserCommitsUseCase;
import ru.x5.devpulse.application.port.in.GetUserProfileUseCase;
import ru.x5.devpulse.application.port.in.GetWeeklyStatsUseCase;
import ru.x5.devpulse.application.port.in.SyncKaitenUsersUseCase;
import ru.x5.devpulse.application.port.out.CollectionRunRepository;
import ru.x5.devpulse.application.port.out.CommitRepository;
import ru.x5.devpulse.application.port.out.DailyStatsRepository;
import ru.x5.devpulse.application.port.out.GitGateway;
import ru.x5.devpulse.application.port.out.KaitenGateway;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;

/**
 * Smoke-тест: поднимает полный Spring-контекст с реальной БД (Testcontainers),
 * проверяет что все use case-ы и порты собраны в граф.
 *
 * <p>Реальные HTTP/Git вызовы НЕ выполняются — внешние сервисы не настроены,
 * проверка идёт только на сборку контекста.</p>
 */
@SpringBootTest(classes = Application.class)
@DisplayName("Smoke: Spring-контекст поднимается, граф полный")
class SmokeApplicationIT {

    /**
     * Singleton-контейнер: НЕ используем {@code @Container}/{@code @Testcontainers}-extension,
     * иначе extension останавливает контейнер на {@code @AfterAll} и следующий тест-класс получает
     * новый контейнер на новом порту (Spring при этом ещё держит закэшированный контекст со старым URL).
     * Static block стартует один раз на JVM, JVM shutdown hook убирает на завершение.
     */
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
    @Autowired GetDashboardUseCase getDashboard;
    // out-ports
    @Autowired GitGateway gitGateway;
    @Autowired KaitenGateway kaitenGateway;
    @Autowired CommitRepository commitRepository;
    @Autowired DailyStatsRepository dailyStatsRepository;
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
                () -> assertThat(getDashboard).as("GetDashboard").isNotNull(),
                // out-ports
                () -> assertThat(gitGateway).as("GitGateway").isNotNull(),
                () -> assertThat(kaitenGateway).as("KaitenGateway").isNotNull(),
                () -> assertThat(commitRepository).as("CommitRepository").isNotNull(),
                () -> assertThat(dailyStatsRepository).as("DailyStatsRepository").isNotNull(),
                () -> assertThat(unifiedUserRepository).as("UnifiedUserRepository").isNotNull(),
                () -> assertThat(collectionRunRepository).as("CollectionRunRepository").isNotNull());
    }
}
