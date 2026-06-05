package ru.x5.devpulse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.x5.devpulse.application.port.out.KaitenGateway;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.model.kaiten.KaitenUser;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.KaitenUserId;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

@ExtendWith(MockitoExtension.class)
@DisplayName("SyncKaitenUsersService (синхронизация пользователей Kaiten)")
class SyncKaitenUsersServiceTest {

    @Mock private KaitenGateway kaitenGateway;
    @Mock private UnifiedUserRepository unifiedUserRepository;
    private SyncKaitenUsersService service;

    @BeforeEach
    void setUp() {
        service = new SyncKaitenUsersService(
                kaitenGateway, unifiedUserRepository, Duration.ofDays(3));
    }

    @Test
    @DisplayName("Непривязанные резолвятся полным сканом по email; чужие пользователи Kaiten игнорируются")
    void resolvesUnlinkedByEmailScan() {
        when(unifiedUserRepository.findAll()).thenReturn(List.of(
                unlinked("a@x5.ru"),
                unlinked("b@x5.ru")));
        // Скан отдаёт двоих наших + чужого, которого в unified_user нет.
        stubStreamUsers(List.of(
                kaitenUser(1L, "a@x5.ru"),
                kaitenUser(99L, "stranger@x5.ru"),
                kaitenUser(2L, "b@x5.ru")));

        int count = service.syncAll();

        assertAll("резолв непривязанных",
                () -> assertThat(count).as("привязано двоих").isEqualTo(2),
                () -> verify(unifiedUserRepository).updateKaitenId(
                        eq(new Email("a@x5.ru")), eq(new KaitenUserId(1L)), any(), any()),
                () -> verify(unifiedUserRepository).updateKaitenId(
                        eq(new Email("b@x5.ru")), eq(new KaitenUserId(2L)), any(), any()),
                // чужого не линкуем
                () -> verify(unifiedUserRepository, never()).updateKaitenId(
                        eq(new Email("stranger@x5.ru")), any(), any(), any()),
                // привязанных нет → точечный fetchUsersByIds не зовём
                () -> verify(kaitenGateway, never()).fetchUsersByIds(anyCollection()));
    }

    @Test
    @DisplayName("Привязанные старше порога рефрешатся точечно по id; полный скан НЕ запускается")
    void refreshesStaleLinkedByIds() {
        UnifiedUser stale = linked("c@x5.ru", 5L, LocalDateTime.now().minusDays(10));
        when(unifiedUserRepository.findAll()).thenReturn(List.of(stale));
        when(kaitenGateway.fetchUsersByIds(anyCollection()))
                .thenReturn(List.of(kaitenUser(5L, "c@x5.ru")));

        int count = service.syncAll();

        assertAll("рефреш привязанного",
                () -> assertThat(count).isEqualTo(1),
                () -> verify(unifiedUserRepository).updateKaitenId(
                        eq(new Email("c@x5.ru")), eq(new KaitenUserId(5L)), any(), any()),
                // непривязанных нет → полного скана нет
                () -> verify(kaitenGateway, never()).streamUsers(any()));
    }

    @Test
    @DisplayName("Все привязаны и свежие → ни одного обращения к Kaiten")
    void skipsFreshLinked() {
        when(unifiedUserRepository.findAll()).thenReturn(List.of(
                linked("d@x5.ru", 7L, LocalDateTime.now().minusHours(1))));

        int count = service.syncAll();

        assertAll("steady-state без API",
                () -> assertThat(count).isZero(),
                () -> verifyNoInteractions(kaitenGateway),
                () -> verify(unifiedUserRepository, never()).updateKaitenId(any(), any(), any(), any()));
    }

    @Test
    @DisplayName("Пустой unified_user → 0 и ни одного обращения к Kaiten")
    void doesNothingWhenNoUsers() {
        when(unifiedUserRepository.findAll()).thenReturn(List.of());

        int count = service.syncAll();

        assertAll("нечего синхронизировать",
                () -> assertThat(count).isZero(),
                () -> verifyNoInteractions(kaitenGateway),
                () -> verifyNoMoreInteractions(unifiedUserRepository));
    }

    @SuppressWarnings("unchecked")
    private void stubStreamUsers(List<KaitenUser> page) {
        org.mockito.Mockito.doAnswer(inv -> {
            Consumer<List<KaitenUser>> handler = inv.getArgument(0);
            handler.accept(page);
            return null;
        }).when(kaitenGateway).streamUsers(any(Consumer.class));
    }

    private static UnifiedUser unlinked(String email) {
        return user(email, null, null);
    }

    private static UnifiedUser linked(String email, long kaitenId, LocalDateTime lastSyncedAt) {
        return user(email, new KaitenUserId(kaitenId), lastSyncedAt);
    }

    private static UnifiedUser user(String email, KaitenUserId kaitenId, LocalDateTime lastSyncedAt) {
        return new UnifiedUser(
                1L, new Email(email), "user", "User", null,
                kaitenId, null, null, false, LocalDateTime.now(), LocalDateTime.now(), lastSyncedAt);
    }

    private static KaitenUser kaitenUser(long id, String email) {
        return new KaitenUser(
                new KaitenUserId(id), new Email(email),
                "user" + id, "User " + id, null, LocalDateTime.now());
    }
}
