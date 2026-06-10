package ru.x5.devpulse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
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
import ru.x5.devpulse.application.port.out.BackgroundRunner;
import ru.x5.devpulse.application.port.out.CollectionAlreadyRunningException;
import ru.x5.devpulse.application.port.out.CollectionLock;
import ru.x5.devpulse.application.port.out.CollectionRunRepository;
import ru.x5.devpulse.application.port.out.KaitenCardsCache;
import ru.x5.devpulse.domain.model.collection.CollectionRun;
import ru.x5.devpulse.domain.model.collection.CollectionStatus;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Тест orchestrator-уровня (async POST). {@code run()} возвращает RUNNING и диспатчит сбор в фон;
 * в тестах {@link BackgroundRunner} выполняет задачу inline, поэтому терминальный статус проверяем
 * по <b>сохранённому</b> прогону (последний {@code save}), а не по возвращаемому значению.
 * Сама git-фаза — в {@link CollectGitStatsServiceTest}, sync — в {@link SyncKaitenUsersServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CollectDailyStatsService (orchestrator: async lock + run lifecycle + delegation)")
class CollectDailyStatsServiceTest {

    private static final LocalDateTime SINCE = LocalDateTime.of(2026, 5, 1, 0, 0);

    @Mock private CollectGitStatsUseCase collectGitStats;
    @Mock private SyncKaitenUsersUseCase syncKaitenUsers;
    @Mock private CollectReviewsUseCase collectReviews;
    @Mock private CollectionRunRepository collectionRunRepository;
    @Mock private CollectionLock collectionLock;
    @Mock private CollectionLock.Handle lockHandle;
    @Mock private KaitenCardsCache kaitenCardsCache;
    @Mock private BackgroundRunner backgroundRunner;

    @InjectMocks private CollectDailyStatsService service;

    @BeforeEach
    void setup() {
        lenient().when(collectionLock.acquireOrThrow()).thenReturn(lockHandle);
        // BackgroundRunner выполняет задачу синхронно — тесты остаются детерминированными.
        lenient().doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(backgroundRunner).run(any());
    }

    /** Последний сохранённый прогон — терминальный (RUNNING сохраняется в прологе, терминал — в фоне). */
    private CollectionRun lastSaved() {
        ArgumentCaptor<CollectionRun> captor = ArgumentCaptor.forClass(CollectionRun.class);
        verify(collectionRunRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Happy path: 202(RUNNING) → фон: git + kaiten → SUCCESS, лок освобождён")
    void happyPath() {
        when(collectGitStats.collect(any(), any(), any())).thenReturn(Set.of(new Email("boris@x5.ru")));

        CollectionRun result = service.run(SINCE);

        ArgumentCaptor<CollectionRun> runs = ArgumentCaptor.forClass(CollectionRun.class);
        verify(collectionRunRepository, times(2)).save(runs.capture());
        assertAll("orchestration",
                () -> assertThat(result.status()).as("POST отдаёт RUNNING (202)")
                        .isEqualTo(CollectionStatus.RUNNING),
                () -> assertThat(runs.getAllValues().get(0).status()).as("сначала — RUNNING")
                        .isEqualTo(CollectionStatus.RUNNING),
                () -> assertThat(runs.getAllValues().get(1).status()).as("в фоне — SUCCESS")
                        .isEqualTo(CollectionStatus.SUCCESS),
                () -> verify(collectGitStats).collect(any(), any(), any()),
                () -> verify(syncKaitenUsers).syncAll(),
                () -> verify(lockHandle).close());
    }

    @Test
    @DisplayName("run() возвращает RUNNING сразу и диспатчит сбор в фон (202-семантика)")
    void dispatchesToBackgroundAndReturnsRunning() {
        when(collectGitStats.collect(any(), any(), any())).thenReturn(Set.of());

        CollectionRun result = service.run(SINCE);

        assertAll("async-диспатч",
                () -> assertThat(result.status()).isEqualTo(CollectionStatus.RUNNING),
                () -> verify(backgroundRunner).run(any()));
    }

    @Test
    @DisplayName("Git упал ⇒ FAILED, Kaiten НЕ зовём")
    void gitFailureMarksFailedAndSkipsKaiten() {
        when(collectGitStats.collect(any(), any(), any())).thenThrow(new RuntimeException("boom"));

        service.run(SINCE);

        CollectionRun terminal = lastSaved();
        assertAll("git упал",
                () -> assertThat(terminal.status()).isEqualTo(CollectionStatus.FAILED),
                () -> assertThat(terminal.error()).hasValue("boom"),
                () -> verify(syncKaitenUsers, never()).syncAll(),
                () -> verify(collectionRunRepository, times(2)).save(any()));
    }

    @Test
    @DisplayName("Отмена в git-фазе ⇒ CANCELLED, kaiten/reviews/cache не зовём")
    void cancellationMarksCancelled() {
        when(collectGitStats.collect(any(), any(), any()))
                .thenThrow(new CollectionCancelledException("cancelled by op"));

        service.run(SINCE);

        CollectionRun terminal = lastSaved();
        assertAll("отмена",
                () -> assertThat(terminal.status()).isEqualTo(CollectionStatus.CANCELLED),
                () -> assertThat(terminal.error()).hasValue("cancelled by op"),
                () -> verify(syncKaitenUsers, never()).syncAll(),
                () -> verify(kaitenCardsCache, never()).invalidateAll());
    }

    @Test
    @DisplayName("Kaiten упал, git ок ⇒ run = SUCCESS (изоляция фаз)")
    void kaitenFailureIsIsolated() {
        when(collectGitStats.collect(any(), any(), any())).thenReturn(Set.of());
        when(syncKaitenUsers.syncAll()).thenThrow(new RuntimeException("kaiten 429"));

        service.run(SINCE);

        assertThat(lastSaved().status()).isEqualTo(CollectionStatus.SUCCESS);
    }

    @Test
    @DisplayName("Happy path зовёт фазу reviews; её падение изолировано ⇒ SUCCESS")
    void reviewsPhaseRunsAndIsIsolated() {
        when(collectGitStats.collect(any(), any(), any())).thenReturn(Set.of());
        doThrow(new RuntimeException("gitlab unreachable")).when(collectReviews).collect(any());

        service.run(SINCE);

        assertAll("reviews-фаза",
                () -> assertThat(lastSaved().status()).isEqualTo(CollectionStatus.SUCCESS),
                () -> verify(collectReviews).collect(any()));
    }

    @Test
    @DisplayName("since=null + есть последний SUCCESS ⇒ стартуем с lastUntil + 1 сек")
    void resolveSinceFromLastSuccess() {
        LocalDateTime lastUntil = LocalDateTime.of(2026, 5, 20, 10, 0);
        when(collectionRunRepository.findLastSuccessfulUntil()).thenReturn(Optional.of(lastUntil));
        when(collectGitStats.collect(any(), any(), any())).thenReturn(Set.of());

        service.run(null);

        ArgumentCaptor<CollectionRun> runs = ArgumentCaptor.forClass(CollectionRun.class);
        verify(collectionRunRepository, atLeastOnce()).save(runs.capture());
        assertThat(runs.getAllValues().get(0).sinceDate()).isEqualTo(lastUntil.plusSeconds(1));
    }

    @Test
    @DisplayName("Пустой период (since>=until) → SUCCESS сразу, фон не диспатчится, лок освобождён")
    void emptyPeriodShortCircuits() {
        LocalDateTime future = LocalDateTime.now().plusDays(1); // effectiveSince >= until(now)

        CollectionRun result = service.run(future);

        assertAll("короткое замыкание пустого периода",
                () -> assertThat(result.status()).isEqualTo(CollectionStatus.SUCCESS),
                () -> verify(backgroundRunner, never()).run(any()),
                () -> verify(lockHandle).close(),
                () -> verifyNoInteractions(collectGitStats));
    }

    @Test
    @DisplayName("Lock занят ⇒ 409 синхронно, ничего не дёрнули, фон не диспатчится")
    void lockAlreadyHeldThrowsAndStartsNothing() {
        when(collectionLock.acquireOrThrow())
                .thenThrow(new CollectionAlreadyRunningException());

        assertThatThrownBy(() -> service.run(SINCE))
                .isInstanceOf(CollectionAlreadyRunningException.class);

        assertAll("ничего не сделано",
                () -> verifyNoInteractions(collectGitStats),
                () -> verifyNoInteractions(syncKaitenUsers),
                () -> verifyNoInteractions(collectionRunRepository),
                () -> verifyNoInteractions(backgroundRunner),
                () -> verifyNoInteractions(lockHandle));
    }

    @Test
    @DisplayName("Lock освобождается после завершения фонового сбора")
    void lockReleasedAfterRun() {
        when(collectGitStats.collect(any(), any(), any())).thenReturn(Set.of());

        service.run(SINCE);

        verify(lockHandle, times(1)).close();
    }

    @Test
    @DisplayName("После SUCCESS — кэш Kaiten cards инвалидируется (фронт сразу видит свежие)")
    void cacheInvalidatedAfterSuccess() {
        when(collectGitStats.collect(any(), any(), any())).thenReturn(Set.of());

        service.run(SINCE);

        verify(kaitenCardsCache, times(1)).invalidateAll();
    }

    @Test
    @DisplayName("При FAILED git — кэш НЕ инвалидируется")
    void cacheNotInvalidatedAfterGitFailure() {
        when(collectGitStats.collect(any(), any(), any())).thenThrow(new RuntimeException("git boom"));

        service.run(SINCE);

        verify(kaitenCardsCache, never()).invalidateAll();
    }

    @Test
    @DisplayName("Падение invalidateAll НЕ ломает уже-успешный сбор")
    void cacheInvalidationFailureDoesNotBreakSuccess() {
        when(collectGitStats.collect(any(), any(), any())).thenReturn(Set.of());
        doThrow(new RuntimeException("cache impl boom"))
                .when(kaitenCardsCache).invalidateAll();

        service.run(SINCE);

        // сбор успешен, ошибка кэша только в логе
        assertThat(lastSaved().status()).isEqualTo(CollectionStatus.SUCCESS);
    }
}
