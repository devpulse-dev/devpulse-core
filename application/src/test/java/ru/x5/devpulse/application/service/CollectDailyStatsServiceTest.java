package ru.x5.devpulse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.x5.devpulse.application.port.in.CollectGitStatsUseCase;
import ru.x5.devpulse.application.port.in.CollectReviewsUseCase;
import ru.x5.devpulse.application.port.in.SyncKaitenUsersUseCase;
import ru.x5.devpulse.application.port.out.CollectionAlreadyRunningException;
import ru.x5.devpulse.application.port.out.CollectionLock;
import ru.x5.devpulse.application.port.out.CollectionRunRepository;
import ru.x5.devpulse.application.port.out.KaitenCardsCache;
import ru.x5.devpulse.domain.model.collection.CollectionRun;
import ru.x5.devpulse.domain.model.collection.CollectionStatus;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Тест orchestrator-уровня. Проверяет lifecycle CollectionRun, distributed lock и
 * корректное делегирование двум worker'ам. Сама git-фаза покрывается отдельно в
 * {@link CollectGitStatsServiceTest}, sync — в {@link SyncKaitenUsersServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CollectDailyStatsService (orchestrator: lock + run lifecycle + delegation)")
class CollectDailyStatsServiceTest {

    private static final LocalDateTime SINCE = LocalDateTime.of(2026, 5, 1, 0, 0);

    @Mock private CollectGitStatsUseCase collectGitStats;
    @Mock private SyncKaitenUsersUseCase syncKaitenUsers;
    @Mock private CollectReviewsUseCase collectReviews;
    @Mock private CollectionRunRepository collectionRunRepository;
    @Mock private CollectionLock collectionLock;
    @Mock private CollectionLock.Handle lockHandle;
    @Mock private KaitenCardsCache kaitenCardsCache;

    @InjectMocks private CollectDailyStatsService service;

    @BeforeEach
    void stubLockAcquired() {
        lenient().when(collectionLock.acquireOrThrow()).thenReturn(lockHandle);
    }

    @Test
    @DisplayName("Happy path: lock → STARTED → git + kaiten → SUCCESS")
    void happyPath() {
        when(collectGitStats.collect(any(), any())).thenReturn(Set.of(new Email("boris@x5.ru")));

        CollectionRun result = service.run(SINCE);

        ArgumentCaptor<CollectionRun> runs = ArgumentCaptor.forClass(CollectionRun.class);
        verify(collectionRunRepository, times(2)).save(runs.capture());
        assertAll("orchestration",
                () -> assertThat(result.status()).isEqualTo(CollectionStatus.SUCCESS),
                () -> verify(collectGitStats).collect(any(), any()),
                () -> verify(syncKaitenUsers).syncAll(),
                () -> verify(lockHandle).close());
    }

    @Test
    @DisplayName("Git упал ⇒ FAILED, Kaiten НЕ зовём")
    void gitFailureMarksFailedAndSkipsKaiten() {
        when(collectGitStats.collect(any(), any())).thenThrow(new RuntimeException("boom"));

        CollectionRun result = service.run(SINCE);

        assertAll("git упал",
                () -> assertThat(result.status()).isEqualTo(CollectionStatus.FAILED),
                () -> assertThat(result.error()).hasValue("boom"),
                () -> verify(syncKaitenUsers, never()).syncAll(),
                () -> verify(collectionRunRepository, times(2)).save(any()));
    }

    @Test
    @DisplayName("Kaiten упал, git ок ⇒ run = SUCCESS (изоляция фаз)")
    void kaitenFailureIsIsolated() {
        when(collectGitStats.collect(any(), any())).thenReturn(Set.of());
        when(syncKaitenUsers.syncAll()).thenThrow(new RuntimeException("kaiten 429"));

        CollectionRun result = service.run(SINCE);

        assertThat(result.status()).isEqualTo(CollectionStatus.SUCCESS);
    }

    @Test
    @DisplayName("Happy path зовёт фазу reviews; её падение изолировано ⇒ SUCCESS")
    void reviewsPhaseRunsAndIsIsolated() {
        when(collectGitStats.collect(any(), any())).thenReturn(Set.of());
        doThrow(new RuntimeException("gitlab unreachable")).when(collectReviews).collect(any());

        CollectionRun result = service.run(SINCE);

        assertAll("reviews-фаза",
                () -> assertThat(result.status()).isEqualTo(CollectionStatus.SUCCESS),
                () -> verify(collectReviews).collect(any()));
    }

    @Test
    @DisplayName("since=null + есть последний SUCCESS ⇒ стартуем с lastUntil + 1 сек")
    void resolveSinceFromLastSuccess() {
        LocalDateTime lastUntil = LocalDateTime.of(2026, 5, 20, 10, 0);
        when(collectionRunRepository.findLastSuccessfulUntil()).thenReturn(Optional.of(lastUntil));
        when(collectGitStats.collect(any(), any())).thenReturn(Set.of());

        service.run(null);

        ArgumentCaptor<CollectionRun> runs = ArgumentCaptor.forClass(CollectionRun.class);
        verify(collectionRunRepository, atLeastOnce()).save(runs.capture());
        assertThat(runs.getAllValues().get(0).sinceDate()).isEqualTo(lastUntil.plusSeconds(1));
    }

    @Test
    @DisplayName("Lock занят ⇒ 409, ничего не дёрнули, handle.close НЕ зовётся")
    void lockAlreadyHeldThrowsAndStartsNothing() {
        when(collectionLock.acquireOrThrow())
                .thenThrow(new CollectionAlreadyRunningException());

        assertThatThrownBy(() -> service.run(SINCE))
                .isInstanceOf(CollectionAlreadyRunningException.class);

        assertAll("ничего не сделано",
                () -> verifyNoInteractions(collectGitStats),
                () -> verifyNoInteractions(syncKaitenUsers),
                () -> verifyNoInteractions(collectionRunRepository),
                () -> verifyNoInteractions(lockHandle));
    }

    @Test
    @DisplayName("Lock освобождается после успешного сбора (try-with-resources)")
    void lockReleasedAfterSuccess() {
        when(collectGitStats.collect(any(), any())).thenReturn(Set.of());

        service.run(SINCE);

        verify(lockHandle, times(1)).close();
    }

    @Test
    @DisplayName("После SUCCESS — кэш Kaiten cards инвалидируется (фронт сразу видит свежие)")
    void cacheInvalidatedAfterSuccess() {
        when(collectGitStats.collect(any(), any())).thenReturn(Set.of());

        service.run(SINCE);

        verify(kaitenCardsCache, times(1)).invalidateAll();
    }

    @Test
    @DisplayName("При FAILED git — кэш НЕ инвалидируется (зачем тратить если данные не свежее)")
    void cacheNotInvalidatedAfterGitFailure() {
        when(collectGitStats.collect(any(), any())).thenThrow(new RuntimeException("git boom"));

        service.run(SINCE);

        verify(kaitenCardsCache, never()).invalidateAll();
    }

    @Test
    @DisplayName("Падение invalidateAll НЕ ломает уже-успешный сбор")
    void cacheInvalidationFailureDoesNotBreakSuccess() {
        when(collectGitStats.collect(any(), any())).thenReturn(Set.of());
        doThrow(new RuntimeException("cache impl boom"))
                .when(kaitenCardsCache).invalidateAll();

        CollectionRun result = service.run(SINCE);

        // сбор успешен, ошибка кэша только в логе
        assertThat(result.status()).isEqualTo(CollectionStatus.SUCCESS);
    }
}
