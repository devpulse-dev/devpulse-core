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
import java.util.Collection;
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
@DisplayName("CollectGitStatsService: git phase (stream + per-repo sweep + single recompute)")
class CollectGitStatsServiceTest {

    private static final RepoName REPO = new RepoName("xrg-core");
    private static final Email AUTHOR = new Email("boris@x5.ru");
    private static final String SHA_NEW = "a".repeat(40);
    private static final String SHA_DUP = "b".repeat(40);
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
    @DisplayName("Sweep zombies: deleteZombies вызывается per-repo с repo и period (set-разность в БД)")
    void sweepsZombiesPerRepo() {
        when(gitGateway.configuredRepos()).thenReturn(List.of(REPO));
        stubStreamCommits(List.of(commit(SHA_NEW)));
        when(commitRepository.findExistingHashes(anyCollection())).thenReturn(Set.of());

        service.collect(SINCE, UNTIL);

        // Какие именно строки — зомби, решает БД по collected_at; здесь проверяем сам вызов sweep'а.
        verify(commitRepository).deleteZombies(eq(REPO), any(), any());
    }

    @Test
    @DisplayName("Existing-коммиты помечаются markSeen — защита от sweep'а")
    void marksExistingCommitsAsSeen() {
        when(gitGateway.configuredRepos()).thenReturn(List.of(REPO));
        stubStreamCommits(List.of(commit(SHA_NEW), commit(SHA_DUP)));
        when(commitRepository.findExistingHashes(anyCollection()))
                .thenReturn(Set.of(new CommitHash(SHA_DUP)));

        service.collect(SINCE, UNTIL);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<CommitHash>> seen = ArgumentCaptor.forClass(Collection.class);
        verify(commitRepository).markSeen(seen.capture(), any());
        assertThat(seen.getValue()).containsExactly(new CommitHash(SHA_DUP));
    }

    @Test
    @DisplayName("Нет коммитов → recompute НЕ зовётся, но sweep всё равно выполняется")
    void noCommitsNoRecompute() {
        when(gitGateway.configuredRepos()).thenReturn(List.of(REPO));
        doAnswer(inv -> null)
                .when(gitGateway).streamCommits(eq(REPO), any(), any(), any());

        Set<Email> affected = service.collect(SINCE, UNTIL);

        assertAll("без коммитов: recompute пропущен, sweep выполнен (БД решит что чистить)",
                () -> assertThat(affected).isEmpty(),
                () -> verify(dailyStatsRepository, never()).recomputeFromCommits(any(), any()),
                () -> verify(commitRepository).deleteZombies(eq(REPO), any(), any()));
    }

    @Test
    @DisplayName("Recompute — один на весь прогон (union авторов всех репо), sweep — per-repo")
    void singleRecomputeForWholeRun() {
        RepoName r1 = new RepoName("r1");
        RepoName r2 = new RepoName("r2");
        when(gitGateway.configuredRepos()).thenReturn(List.of(r1, r2));
        // оба репо отдают по коммиту одного и того же автора
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Consumer<List<Commit>> handler = inv.getArgument(3);
            handler.accept(List.of(commit(SHA_NEW)));
            return null;
        }).when(gitGateway).streamCommits(any(), any(), any(), any());
        when(commitRepository.findExistingHashes(anyCollection())).thenReturn(Set.of());

        service.collect(SINCE, UNTIL);

        assertAll("single recompute, per-repo sweep",
                // recompute ровно один раз с объединением затронутых авторов (без O(K²))
                () -> verify(dailyStatsRepository, times(1))
                        .recomputeFromCommits(eq(Set.of(AUTHOR)), any()),
                // sweep — по разу на каждый репо
                () -> verify(commitRepository, times(2)).deleteZombies(any(), any(), any()),
                // recompute обёрнут ровно в одну tx на прогон
                () -> verify(transactionRunner, times(1)).inTransaction(any(Supplier.class)));
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
