package ru.x5.devpulse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
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
import ru.x5.devpulse.application.port.out.CommitRepository;
import ru.x5.devpulse.application.port.out.DailyStatsRepository;
import ru.x5.devpulse.application.port.out.GitGateway;
import ru.x5.devpulse.application.port.out.TransactionRunner;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.common.TaskNumber;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.git.CommitHash;
import ru.x5.devpulse.domain.model.git.RepoName;
import ru.x5.devpulse.domain.model.user.Email;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollectGitStatsService: git phase (stream + cleanup zombies + recompute per-repo)")
class CollectGitStatsServiceTest {

    private static final RepoName REPO = new RepoName("xrg-core");
    private static final Email AUTHOR = new Email("boris@x5.ru");
    private static final String SHA_NEW = "a".repeat(40);
    private static final String SHA_DUP = "b".repeat(40);
    private static final String SHA_ZOMBIE = "c".repeat(40);
    private static final LocalDateTime SINCE = LocalDateTime.of(2026, 5, 1, 0, 0);
    private static final LocalDateTime UNTIL = LocalDateTime.of(2026, 5, 31, 23, 59);

    @Mock private GitGateway gitGateway;
    @Mock private CommitRepository commitRepository;
    @Mock private DailyStatsRepository dailyStatsRepository;
    @Mock private UnifiedUserRepository unifiedUserRepository;
    @Mock private TransactionRunner transactionRunner;

    @InjectMocks private CollectGitStatsService service;

    @BeforeEach
    void txRunnerCallsSupplierSynchronously() {
        // lenient — некоторые тесты могут не входить в tx-блок.
        lenient().when(transactionRunner.inTransaction(any(Supplier.class)))
                .thenAnswer(inv -> {
                    Supplier<?> supplier = inv.getArgument(0);
                    return supplier.get();
                });
    }

    @Test
    @DisplayName("Happy path: дубль отфильтрован, recompute зовётся для затронутого автора")
    void happyPath() {
        when(gitGateway.configuredRepos()).thenReturn(List.of(REPO));
        stubStreamCommits(List.of(commit(SHA_NEW), commit(SHA_DUP)));
        when(commitRepository.findExistingHashes(anyCollection()))
                .thenReturn(Set.of(new CommitHash(SHA_DUP)));
        when(commitRepository.findHashesByRepoAndPeriod(eq(REPO), any()))
                .thenReturn(Set.of(new CommitHash(SHA_NEW), new CommitHash(SHA_DUP)));

        Set<Email> affected = service.collect(SINCE, UNTIL);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Commit>> savedCommits = ArgumentCaptor.forClass(List.class);
        verify(commitRepository).saveAll(savedCommits.capture(), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Email>> recomputeEmails = ArgumentCaptor.forClass(Set.class);
        verify(dailyStatsRepository).recomputeFromCommits(recomputeEmails.capture(), any());

        assertAll("happy path",
                () -> assertThat(savedCommits.getValue())
                        .extracting(c -> c.hash().value())
                        .containsExactly(SHA_NEW),
                () -> assertThat(recomputeEmails.getValue()).containsExactly(AUTHOR),
                () -> assertThat(affected).containsExactly(AUTHOR));
    }

    @Test
    @DisplayName("Cleanup zombies: то что в БД есть, но в git нет — удаляется")
    void cleansUpRebaseZombies() {
        when(gitGateway.configuredRepos()).thenReturn(List.of(REPO));
        stubStreamCommits(List.of(commit(SHA_NEW)));
        when(commitRepository.findExistingHashes(anyCollection())).thenReturn(Set.of());
        when(commitRepository.findHashesByRepoAndPeriod(eq(REPO), any()))
                .thenReturn(Set.of(new CommitHash(SHA_NEW), new CommitHash(SHA_ZOMBIE)));

        service.collect(SINCE, UNTIL);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<CommitHash>> deleted = ArgumentCaptor.forClass(Set.class);
        verify(commitRepository).deleteByHashes(deleted.capture());
        assertThat(deleted.getValue()).containsExactly(new CommitHash(SHA_ZOMBIE));
    }

    @Test
    @DisplayName("Нет zombies → deleteByHashes не вызывается")
    void noZombiesNoDelete() {
        when(gitGateway.configuredRepos()).thenReturn(List.of(REPO));
        stubStreamCommits(List.of(commit(SHA_NEW)));
        when(commitRepository.findExistingHashes(anyCollection())).thenReturn(Set.of());
        when(commitRepository.findHashesByRepoAndPeriod(eq(REPO), any()))
                .thenReturn(Set.of(new CommitHash(SHA_NEW)));

        service.collect(SINCE, UNTIL);

        verify(commitRepository, never()).deleteByHashes(anyCollection());
    }

    @Test
    @DisplayName("Нет коммитов и нет zombies → recompute НЕ зовётся")
    void noCommitsNoRecompute() {
        when(gitGateway.configuredRepos()).thenReturn(List.of(REPO));
        doAnswer(inv -> null)
                .when(gitGateway).streamCommits(eq(REPO), any(), any(), any());
        when(commitRepository.findHashesByRepoAndPeriod(eq(REPO), any())).thenReturn(Set.of());

        Set<Email> affected = service.collect(SINCE, UNTIL);

        assertAll("nothing happened",
                () -> assertThat(affected).isEmpty(),
                () -> verify(dailyStatsRepository, never()).recomputeFromCommits(any(), any()),
                () -> verify(commitRepository, never()).deleteByHashes(anyCollection()));
    }

    @Test
    @DisplayName("Каждый repo — своя финальная tx (N репо → N tx-блоков)")
    void oneTransactionPerRepo() {
        RepoName r1 = new RepoName("r1");
        RepoName r2 = new RepoName("r2");
        when(gitGateway.configuredRepos()).thenReturn(List.of(r1, r2));
        doAnswer(inv -> null).when(gitGateway).streamCommits(any(), any(), any(), any());
        when(commitRepository.findHashesByRepoAndPeriod(any(), any())).thenReturn(Set.of());

        service.collect(SINCE, UNTIL);

        verify(transactionRunner, times(2)).inTransaction(any(Supplier.class));
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
}
