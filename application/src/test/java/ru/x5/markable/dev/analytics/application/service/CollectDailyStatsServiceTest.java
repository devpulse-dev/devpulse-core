package ru.x5.markable.dev.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.x5.markable.dev.analytics.application.port.out.CollectionRunRepository;
import ru.x5.markable.dev.analytics.application.port.out.CommitRepository;
import ru.x5.markable.dev.analytics.application.port.out.DailyStatsRepository;
import ru.x5.markable.dev.analytics.application.port.out.GitGateway;
import ru.x5.markable.dev.analytics.application.port.out.KaitenGateway;
import ru.x5.markable.dev.analytics.application.port.out.KaitenUserRepository;
import ru.x5.markable.dev.analytics.application.port.out.UnifiedUserRepository;
import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.common.TaskNumber;
import ru.x5.markable.dev.analytics.domain.model.collection.CollectionRun;
import ru.x5.markable.dev.analytics.domain.model.collection.CollectionStatus;
import ru.x5.markable.dev.analytics.domain.model.git.Commit;
import ru.x5.markable.dev.analytics.domain.model.git.CommitHash;
import ru.x5.markable.dev.analytics.domain.model.git.RepoName;
import ru.x5.markable.dev.analytics.domain.model.kaiten.KaitenUser;
import ru.x5.markable.dev.analytics.domain.model.user.Email;
import ru.x5.markable.dev.analytics.domain.model.user.KaitenUserId;

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

    @InjectMocks private CollectDailyStatsService service;

    @Test
    @DisplayName("Happy path: сохранение коммитов + recompute daily_stats + sync kaiten users")
    void happyPath() {
        when(gitGateway.configuredRepos()).thenReturn(List.of(REPO));
        when(gitGateway.prepare(REPO)).thenReturn(REPO);
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
        when(gitGateway.prepare(REPO)).thenReturn(REPO);
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
        when(gitGateway.prepare(REPO)).thenReturn(REPO);
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
        when(gitGateway.prepare(REPO)).thenReturn(REPO);
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
        when(gitGateway.prepare(REPO)).thenReturn(REPO);
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
        when(gitGateway.prepare(REPO)).thenReturn(REPO);
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
