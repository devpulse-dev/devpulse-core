package ru.x5.devpulse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.x5.devpulse.application.port.out.KaitenGateway;
import ru.x5.devpulse.application.port.out.KaitenUserRepository;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.model.kaiten.KaitenUser;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.KaitenUserId;

@ExtendWith(MockitoExtension.class)
@DisplayName("SyncKaitenUsersService (синхронизация пользователей Kaiten)")
class SyncKaitenUsersServiceTest {

    @Mock private KaitenGateway kaitenGateway;
    @Mock private KaitenUserRepository kaitenUserRepository;
    @Mock private UnifiedUserRepository unifiedUserRepository;
    @InjectMocks private SyncKaitenUsersService service;

    @Test
    @DisplayName("Тянет всех пользователей: bulk-upsert + проброс kaiten_id в unified_user по email")
    void upsertsAllUsersAndLinksKaitenIdToUnifiedUser() {
        List<KaitenUser> users = List.of(
                kaitenUser(1L, "a@x5.ru"),
                kaitenUser(2L, "b@x5.ru"));
        when(kaitenGateway.fetchAllUsers()).thenReturn(users);

        int count = service.syncAll();

        assertAll("полный sync",
                () -> assertThat(count).as("вернули число синхронизированных").isEqualTo(2),
                () -> verify(kaitenUserRepository).upsertAll(users),
                () -> verify(unifiedUserRepository).updateKaitenId(
                        eq(new Email("a@x5.ru")), eq(new KaitenUserId(1L)), any(), any()),
                () -> verify(unifiedUserRepository).updateKaitenId(
                        eq(new Email("b@x5.ru")), eq(new KaitenUserId(2L)), any(), any()));
    }

    @Test
    @DisplayName("Пользователь без email — НЕ привязывается к unified_user (защита от NPE на email==null)")
    void skipsLinkingWhenEmailIsNull() {
        when(kaitenGateway.fetchAllUsers()).thenReturn(List.of(
                new KaitenUser(new KaitenUserId(3L), null, "svc-account", "Svc", null, LocalDateTime.now())));

        service.syncAll();

        verify(unifiedUserRepository, never()).updateKaitenId(any(), any(), any(), any());
    }

    @Test
    @DisplayName("На пустом ответе Kaiten — возвращает 0 и НЕ зовёт upsert/link")
    void doesNothingOnEmptyResponse() {
        when(kaitenGateway.fetchAllUsers()).thenReturn(List.of());

        int count = service.syncAll();

        assertAll("пустая синхронизация",
                () -> assertThat(count).isZero(),
                () -> verify(kaitenUserRepository, never()).upsertAll(org.mockito.ArgumentMatchers.anyCollection()),
                () -> verifyNoInteractions(unifiedUserRepository));
    }

    private static KaitenUser kaitenUser(long id, String email) {
        return new KaitenUser(
                new KaitenUserId(id),
                new Email(email),
                "user" + id,
                "User " + id,
                null,
                LocalDateTime.now());
    }
}
