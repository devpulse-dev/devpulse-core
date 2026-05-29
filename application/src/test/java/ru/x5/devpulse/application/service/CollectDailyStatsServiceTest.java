package ru.x5.devpulse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.x5.devpulse.application.port.out.CollectionAlreadyRunningException;
import ru.x5.devpulse.application.port.out.CollectionLock;
import ru.x5.devpulse.application.port.out.CollectionRunRepository;
import ru.x5.devpulse.application.port.out.CommitRepository;
import ru.x5.devpulse.application.port.out.DailyStatsRepository;
import ru.x5.devpulse.application.port.out.GitGateway;
import ru.x5.devpulse.application.port.out.KaitenGateway;
import ru.x5.devpulse.application.port.out.KaitenUserRepository;
import ru.x5.devpulse.application.port.out.TransactionRunner;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.common.TaskNumber;
import ru.x5.devpulse.domain.model.collection.CollectionRun;
import ru.x5.devpulse.domain.model.collection.CollectionStatus;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.git.CommitHash;
import ru.x5.devpulse.domain.model.git.RepoName;
import ru.x5.devpulse.domain.model.kaiten.KaitenUser;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.KaitenUserId;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollectDailyStatsService (git + cleanup zombies + recompute daily_stats + sync kaiten users)")
class CollectDailyStatsServiceTest {

    private static final RepoName REPO = new RepoName("xrg-core");
    private static final Email AUTHOR = new Email("boris@x5.ru");
    private static final String SHA_NEW = "a".repeat(40);
    private static final String SHA_DUP = "b".repeat(40);
    private static final String SHA_ZOMBIE = "c".repeat(40);
    private static final LocalDateTime SINCE = LocalDateTime.of(2026, 5, 1, 0, 0);

    @Mock private GitGateway gitGateway;
    @Mock private KaitenGateway kaitenGateway;
    @Mock private CommitRepository commitRepository;
    @Mock private DailyStatsRepository dailyStatsRepository;
    @Mock private KaitenUserRepository kaitenUserRepository;
    @Mock private UnifiedUserRepository unifiedUserRepository;
    @Mock private CollectionRunRepository collectionRunRepository;
    @Mock private CollectionLock collectionLock;
    @Mock private CollectionLock.Handle lockHandle;
    @Mock private TransactionRunner transactionRunner;

    @InjectMocks private CollectDailyStatsService service;

    @BeforeEach
    void stubLockAcquired() {
        // Дефолт: lock свободен, handle закрывается без побочных эффектов.
        // lenient — потому что тест "lock занят" переопределяет этот стаб, и STRICT_STUBS
        // иначе пожалуется на «не использованный» first stubbing.
        lenient().when(collectionLock.acquireOrThrow()).thenReturn(lockHandle);

        // TransactionRunner.inTransaction(Supplier) — вызываем supplier синхронно.
        // Это эквивалентно "tx работает, всё закоммитилось". Тесты на rollback пишем
        // через бросание exception внутри supplier и проверкой что состояние не тронуто.
        lenient().when(transactionRunner.inTransaction(any(Supplier.class)))
                .thenAnswer(inv -> {
                    Supplier<?> supplier = inv.getArgument(0);
                    return supplier.get();
                });
    }

    @Test
    @DisplayName("Happy path: сохранение коммитов + recompute daily_stats + sync kaiten users")
    void happyPath() {
        when(gitGateway.configuredRepos()).thenReturn(List.of(REPO));
        stubStreamCommits(List.of(commit(SHA_NEW), commit(SHA_DUP)));

        when(commitRepository.findExistingHashes(anyCollection()))
                .thenReturn(Set.of(new CommitHash(SHA_DUP)));
        when(commitRepository.findHashesByRepoAndPeriod(eq(REPO), any()))
                .thenReturn(Set.of(new CommitHash(SHA_NEW), new CommitHash(SHA_DUP)));
        when(kaitenGateway.fetchAllUsers()).thenReturn(List.of(
                kaitenUser(7L, AUTHOR, "Boris")
        ));

        CollectionRun result = service.run(SINCE);

        ArgumentCaptor<CollectionRun> runs = ArgumentCaptor.forClass(CollectionRun.class);
        verify(collectionRunRepository, times(2)).save(runs.capture());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Commit>> savedCommits = ArgumentCaptor.forClass(List.class);
        verify(commitRepository).saveAll(savedCommits.capture());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Email>> recomputeEmails = ArgumentCaptor.forClass(Set.class);
        verify(dailyStatsRepository).recomputeFromCommits(recomputeEmails.capture(), any());

        assertAll("happy path",
                () -> assertThat(result.status()).isEqualTo(CollectionStatus.SUCCESS),
                () -> assertThat(savedCommits.getValue())
                        .as("дубль отфильтрован, сохранили только SHA_NEW")
                        .extracting(c -> c.hash().value())
                        .containsExactly(SHA_NEW),
                () -> assertThat(recomputeEmails.getValue())
                        .as("recompute зван для затронутого автора")
                        .containsExactly(AUTHOR),
                () -> verify(kaitenUserRepository).upsertAll(anyCollection()),
                () -> verify(unifiedUserRepository).updateKaitenId(
                        eq(AUTHOR), eq(new KaitenUserId(7L)), eq("Boris"), any()),
                // НЕ должен зваться старый upsertAll(stats) — мы перешли на recompute.
                () -> verify(dailyStatsRepository, never()).upsertAll(anyCollection()));
    }

    @Test
    @DisplayName("Cleanup zombies: коммиты из БД, отсутствующие в git, удаляются")
    void cleansUpRebaseZombies() {
        when(gitGateway.configuredRepos()).thenReturn(List.of(REPO));
        // git отдал только SHA_NEW
        stubStreamCommits(List.of(commit(SHA_NEW)));
        when(commitRepository.findExistingHashes(anyCollection())).thenReturn(Set.of());
        // В БД лежит SHA_NEW + SHA_ZOMBIE (последний — после rebase удалён из git)
        when(commitRepository.findHashesByRepoAndPeriod(eq(REPO), any()))
                .thenReturn(Set.of(new CommitHash(SHA_NEW), new CommitHash(SHA_ZOMBIE)));
        when(kaitenGateway.fetchAllUsers()).thenReturn(List.of());

        service.run(SINCE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<CommitHash>> deleted = ArgumentCaptor.forClass(Set.class);
        verify(commitRepository).deleteByHashes(deleted.capture());

        assertThat(deleted.getValue())
                .as("должны удалить именно zombie")
                .containsExactly(new CommitHash(SHA_ZOMBIE));
    }

    @Test
    @DisplayName("Нет zombies → deleteByHashes не вызывается вообще")
    void noZombiesNoDelete() {
        when(gitGateway.configuredRepos()).thenReturn(List.of(REPO));
        stubStreamCommits(List.of(commit(SHA_NEW)));
        when(commitRepository.findExistingHashes(anyCollection())).thenReturn(Set.of());
        when(commitRepository.findHashesByRepoAndPeriod(eq(REPO), any()))
                .thenReturn(Set.of(new CommitHash(SHA_NEW)));
        when(kaitenGateway.fetchAllUsers()).thenReturn(List.of());

        service.run(SINCE);

        verify(commitRepository, never()).deleteByHashes(anyCollection());
    }

    @Test
    @DisplayName("Падение git ⇒ FAILED, Kaiten не зовём, recompute не делаем")
    void gitFailureMarksFailedAndSkipsKaiten() {
        when(gitGateway.configuredRepos()).thenReturn(List.of(REPO));
        doThrow(new RuntimeException("boom"))
                .when(gitGateway).streamCommits(any(), any(), any(), any());

        CollectionRun result = service.run(SINCE);

        assertAll("git упал",
                () -> assertThat(result.status()).isEqualTo(CollectionStatus.FAILED),
                () -> assertThat(result.error()).hasValue("boom"),
                () -> verify(kaitenGateway, never()).fetchAllUsers(),
                () -> verify(dailyStatsRepository, never()).recomputeFromCommits(any(), any()),
                () -> verify(collectionRunRepository, times(2)).save(any()));
    }

    @Test
    @DisplayName("Падение Kaiten изолировано ⇒ git stats и recompute сохранены, прогон SUCCESS")
    void kaitenFailureDoesNotRollbackGitStats() {
        when(gitGateway.configuredRepos()).thenReturn(List.of(REPO));
        stubStreamCommits(List.of(commit(SHA_NEW)));
        when(commitRepository.findExistingHashes(anyCollection())).thenReturn(Set.of());
        when(commitRepository.findHashesByRepoAndPeriod(eq(REPO), any()))
                .thenReturn(Set.of(new CommitHash(SHA_NEW)));
        when(kaitenGateway.fetchAllUsers()).thenThrow(new RuntimeException("kaiten 429"));

        CollectionRun result = service.run(SINCE);

        assertAll("kaiten упал, git ок",
                () -> assertThat(result.status()).isEqualTo(CollectionStatus.SUCCESS),
                () -> verify(commitRepository).saveAll(anyCollection()),
                () -> verify(dailyStatsRepository).recomputeFromCommits(anyCollection(), any()),
                () -> verify(kaitenUserRepository, never()).upsertAll(anyCollection()));
    }

    @Test
    @DisplayName("Нет коммитов и нет zombies → recompute НЕ зовём (нет затронутых авторов)")
    void noCommitsNoRecompute() {
        when(gitGateway.configuredRepos()).thenReturn(List.of(REPO));
        // git вообще ничего не вернул
        doAnswer(inv -> null)
                .when(gitGateway).streamCommits(eq(REPO), any(), any(), any());
        when(commitRepository.findHashesByRepoAndPeriod(eq(REPO), any())).thenReturn(Set.of());
        when(kaitenGateway.fetchAllUsers()).thenReturn(List.of());

        service.run(SINCE);

        verify(dailyStatsRepository, never()).recomputeFromCommits(any(), any());
        verify(commitRepository, never()).deleteByHashes(anyCollection());
    }

    @Test
    @DisplayName("since=null + есть последний SUCCESS ⇒ стартуем с lastUntil + 1 секунда")
    void resolveSinceFromLastSuccess() {
        LocalDateTime lastUntil = LocalDateTime.of(2026, 5, 20, 10, 0);
        when(collectionRunRepository.findLastSuccessfulUntil()).thenReturn(Optional.of(lastUntil));
        when(gitGateway.configuredRepos()).thenReturn(List.of());
        when(kaitenGateway.fetchAllUsers()).thenReturn(List.of());

        service.run(null);

        ArgumentCaptor<CollectionRun> runs = ArgumentCaptor.forClass(CollectionRun.class);
        verify(collectionRunRepository, atLeastOnce()).save(runs.capture());

        assertThat(runs.getAllValues().get(0).sinceDate())
                .isEqualTo(lastUntil.plusSeconds(1));
    }

    @Test
    @DisplayName("Каждый repo обрабатывается в своей транзакции (N tx-блоков на N репо)")
    void oneTransactionPerRepo() {
        RepoName r1 = new RepoName("r1");
        RepoName r2 = new RepoName("r2");
        when(gitGateway.configuredRepos()).thenReturn(List.of(r1, r2));
        doAnswer(inv -> null).when(gitGateway).streamCommits(any(), any(), any(), any());
        when(commitRepository.findHashesByRepoAndPeriod(any(), any())).thenReturn(Set.of());
        when(kaitenGateway.fetchAllUsers()).thenReturn(List.of());

        service.run(SINCE);

        // Финальный cleanup+recompute блок зовётся ровно один раз на репо.
        verify(transactionRunner, times(2)).inTransaction(any(Supplier.class));
    }

    @Test
    @DisplayName("Падение в финальной tx (recompute упал) ⇒ run = FAILED, exception в error()")
    void txBlockFailureRollsBackAndMarksFailed() {
        when(gitGateway.configuredRepos()).thenReturn(List.of(REPO));
        stubStreamCommits(List.of(commit(SHA_NEW)));
        when(commitRepository.findExistingHashes(anyCollection())).thenReturn(Set.of());
        when(commitRepository.findHashesByRepoAndPeriod(eq(REPO), any())).thenReturn(Set.of());

        // Имитируем падение recompute. В реальности это означает rollback — кэш zombies/recompute
        // не применился, состояние БД до tx сохранилось.
        doThrow(new RuntimeException("db down"))
                .when(dailyStatsRepository).recomputeFromCommits(anyCollection(), any());

        CollectionRun result = service.run(SINCE);

        assertAll("recompute упал → FAILED, kaiten не зовётся",
                () -> assertThat(result.status()).isEqualTo(CollectionStatus.FAILED),
                () -> assertThat(result.error()).hasValueSatisfying(
                        msg -> assertThat(msg).contains("db down")),
                () -> verify(kaitenGateway, never()).fetchAllUsers());
    }

    @Test
    @DisplayName("Lock занят ⇒ exception, никакой работы не начато, handle.close() не зовётся")
    void lockAlreadyHeld_throwsAndStartsNothing() {
        // Переопределяем дефолтный стаб: lock бросает.
        when(collectionLock.acquireOrThrow())
                .thenThrow(new CollectionAlreadyRunningException());

        assertThatThrownBy(() -> service.run(SINCE))
                .isInstanceOf(CollectionAlreadyRunningException.class);

        // Ни git, ни kaiten, ни запись CollectionRun — всё должно быть не тронуто.
        assertAll("ничего не сделано",
                () -> verifyNoInteractions(gitGateway),
                () -> verifyNoInteractions(kaitenGateway),
                () -> verifyNoInteractions(commitRepository),
                () -> verifyNoInteractions(dailyStatsRepository),
                () -> verifyNoInteractions(kaitenUserRepository),
                () -> verifyNoInteractions(unifiedUserRepository),
                () -> verifyNoInteractions(collectionRunRepository),
                () -> verifyNoInteractions(lockHandle));
    }

    @Test
    @DisplayName("Happy path: handle.close() вызывается ровно один раз после успешного сбора")
    void lockReleasedAfterSuccess() {
        when(gitGateway.configuredRepos()).thenReturn(List.of());
        when(kaitenGateway.fetchAllUsers()).thenReturn(List.of());

        service.run(SINCE);

        verify(lockHandle, times(1)).close();
    }

    /* ------------ helpers ------------ */

    private void stubStreamCommits(List<Commit> batch) {
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Consumer<List<Commit>> handler = inv.getArgument(3);
            handler.accept(batch);
            return null;
        }).when(gitGateway).streamCommits(eq(REPO), any(), any(), any());
    }

    private static Commit commit(String hash) {
        return new Commit(
                new CommitHash(hash),
                AUTHOR,
                LocalDateTime.of(2026, 5, 15, 12, 0),
                false,
                10, 5, 0,
                "TASK-42 fix",
                new TaskNumber("42"),
                REPO);
    }

    private static KaitenUser kaitenUser(long id, Email email, String fullName) {
        return new KaitenUser(new KaitenUserId(id), email, "user" + id, fullName,
                "https://avatar/" + id, LocalDateTime.now());
    }
}
