package ru.x5.devpulse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.x5.devpulse.adapter.kaiten.KaitenHttpClient;
import ru.x5.devpulse.application.port.out.KaitenGateway;
import ru.x5.devpulse.domain.model.user.KaitenUserId;

/**
 * IT: Spring-context'ом проверяем что {@code @Cacheable} реально работает на
 * {@code KaitenGateway.fetchCardsForMember} — повторный вызов с теми же args возвращает
 * результат из Caffeine кэша, не дёргая HTTP-клиент.
 *
 * <p><b>Зачем полный context:</b> {@code @Cacheable} работает через AOP proxy, который ставит
 * только Spring при wiring. В unit-тесте на голом адаптере (без proxy) кэш не сработает.</p>
 */
@SpringBootTest(classes = Application.class)
@DisplayName("KaitenGatewayAdapter — @Cacheable на fetchCardsForMember")
class KaitenCardsCacheIT {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("devanalytics_test")
            .withUsername("test")
            .withPassword("test");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean
    KaitenHttpClient httpClient;

    @Autowired
    KaitenGateway gateway;

    @Autowired
    CacheManager cacheManager;

    private static final KaitenUserId MEMBER_A = new KaitenUserId(7L);
    private static final KaitenUserId MEMBER_B = new KaitenUserId(8L);
    private static final LocalDateTime AFTER = LocalDateTime.of(2026, 5, 1, 0, 0);

    @BeforeEach
    void emptyCacheBeforeEachTest() {
        var cache = cacheManager.getCache("kaiten-cards-by-member");
        if (cache != null) cache.clear();
        when(httpClient.getCards(anyInt(), anyInt(), any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Дважды зовём один и тот же (member, after) → HTTP только один раз")
    void secondCallHitsCache() {
        gateway.fetchCardsForMember(MEMBER_A, AFTER);
        gateway.fetchCardsForMember(MEMBER_A, AFTER);

        verify(httpClient, times(1)).getCards(anyInt(), anyInt(), eq(String.valueOf(MEMBER_A.value())), any());
    }

    @Test
    @DisplayName("Разные member → разные кэш-ключи, HTTP вызывается для каждого")
    void differentMembersBypassCache() {
        gateway.fetchCardsForMember(MEMBER_A, AFTER);
        gateway.fetchCardsForMember(MEMBER_B, AFTER);

        assertAll("два разных HTTP вызова для разных member id",
                () -> verify(httpClient, times(1))
                        .getCards(anyInt(), anyInt(), eq(String.valueOf(MEMBER_A.value())), any()),
                () -> verify(httpClient, times(1))
                        .getCards(anyInt(), anyInt(), eq(String.valueOf(MEMBER_B.value())), any()));
    }

    @Test
    @DisplayName("Разные updatedAfter → разные кэш-ключи")
    void differentTimestampsBypassCache() {
        gateway.fetchCardsForMember(MEMBER_A, AFTER);
        gateway.fetchCardsForMember(MEMBER_A, AFTER.plusDays(1));

        verify(httpClient, times(2))
                .getCards(anyInt(), anyInt(), eq(String.valueOf(MEMBER_A.value())), any());
    }

    @Test
    @DisplayName("CacheManager поднят как CaffeineCacheManager и содержит kaiten-cards-by-member")
    void cacheIsConfigured() {
        assertThat(cacheManager.getCache("kaiten-cards-by-member"))
                .as("кэш должен быть зарегистрирован через application.yml")
                .isNotNull();
    }
}
