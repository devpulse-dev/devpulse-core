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
import java.util.Map;
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
import ru.x5.markable.dev.analytics.application.port.out.KaitenCardRepository;
import ru.x5.markable.dev.analytics.application.port.out.KaitenGateway;
import ru.x5.markable.dev.analytics.application.port.out.UnifiedUserRepository;
import ru.x5.markable.dev.analytics.domain.common.TaskNumber;
import ru.x5.markable.dev.analytics.domain.model.collection.CollectionRun;
import ru.x5.markable.dev.analytics.domain.model.collection.CollectionStatus;
import ru.x5.markable.dev.analytics.domain.model.git.Commit;
import ru.x5.markable.dev.analytics.domain.model.git.CommitHash;
import ru.x5.markable.dev.analytics.domain.model.git.RepoName;
import ru.x5.markable.dev.analytics.domain.model.kaiten.KaitenCard;
import ru.x5.markable.dev.analytics.domain.model.stats.DailyAuthorStats;
import ru.x5.markable.dev.analytics.domain.model.user.Email;
import ru.x5.markable.dev.analytics.domain.model.user.KaitenUserId;
import ru.x5.markable.dev.analytics.domain.model.user.UnifiedUser;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollectDailyStatsService (оркестрация Git + Kaiten + журнал прогона)")
class CollectDailyStatsServiceTest {

    private static final RepoName REPO = new RepoName("xrg-core");
    private static final Email AUTHOR = new Email("boris@x5.ru");
    private static final String SHA_NEW = "a".repeat(40);
    private static final String SHA_DUP = "b".repeat(40);
    private static final LocalDateTime SINCE = LocalDateTime.of(2026, 5, 1, 0, 0);

    @Mock private GitGateway gitGateway;
    @Mock private KaitenGateway kaitenGateway;
    @Mock private CommitRepository commitRepository;
    @Mock private DailyStatsRepository dailyStatsRepository;
    @Mock private KaitenCardRepository kaitenCardRepository;
    @Mock private UnifiedUserRepository unifiedUserRepository;
    @Mock private CollectionRunRepository collectionRunRepository;

    @InjectMocks private CollectDailyStatsService service;

    @Test
    @DisplayName("Happy path: git → commits + daily stats → kaiten cards, прогон фиксируется как SUCCESS")
    void happyPathSavesAllAndMarksSuccess() {
        // git: один репо, batch из 2 коммитов (один новый, один уже в БД)
        when(gitGateway.configuredRepos()).thenReturn(List.of(REPO));
        when(gitGateway.prepare(REPO)).thenReturn(REPO);
        stubStreamCommits(List.of(commit(SHA_NEW), commit(SHA_DUP)));

        when(commitRepository.findExistingHashes(anyCollection()))
                .thenReturn(Set.of(new CommitHash(SHA_DUP)));
        when(unifiedUserRepository.findOrCreateAll(anyCollection()))
                .thenReturn(Map.of(AUTHOR, 42L));

        // kaiten: один пользователь с kaiten_id → одна страница из одной карточки
        when(unifiedUserRepository.findAll()).thenReturn(List.of(userWithKaiten(42L, 7L)));
        stubStreamCards(List.of(card(100L)));

        CollectionRun result = service.run(SINCE);

        ArgumentCaptor<CollectionRun> runs = ArgumentCaptor.forClass(CollectionRun.class);
        verify(collectionRunRepository, times(2)).save(runs.capture());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Commit>> savedCommits = ArgumentCaptor.forClass(List.class);
        verify(commitRepository).saveAll(savedCommits.capture());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DailyAuthorStats>> savedStats = ArgumentCaptor.forClass(List.class);
        verify(dailyStatsRepository).upsertAll(savedStats.capture());

        assertAll("happy path",
                () -> assertThat(result.status())
                        .as("итоговый статус").isEqualTo(CollectionStatus.SUCCESS),
                () -> assertThat(runs.getAllValues().get(0).status())
                        .as("первая запись — RUNNING").isEqualTo(CollectionStatus.RUNNING),
                () -> assertThat(runs.getAllValues().get(1).status())
                        .as("вторая — SUCCESS").isEqualTo(CollectionStatus.SUCCESS),
                () -> assertThat(savedCommits.getValue())
                        .as("дубликат отфильтрован, сохраняется только новый коммит")
                        .extracting(c -> c.hash().value())
                        .containsExactly(SHA_NEW),
                () -> assertThat(savedStats.getValue())
                        .as("daily stat по одному ключу (email,date,repo) с проставленным userId")
                        .hasSize(1)
                        .first()
                        .satisfies(s -> {
                            assertThat(s.userId()).isEqualTo(42L);
                            assertThat(s.repo()).isEqualTo(REPO);
                            assertThat(s.authorEmail()).isEqualTo(AUTHOR);
                        }),
                () -> verify(kaitenCardRepository).upsertAll(anyCollection()));
    }

    @Test
    @DisplayName("Падение git ⇒ FAILED, Kaiten НЕ дёргается")
    void gitFailureMarksFailedAndSkipsKaiten() {
        when(gitGateway.configuredRepos()).thenReturn(List.of(REPO));
        when(gitGateway.prepare(REPO)).thenReturn(REPO);
        doThrow(new RuntimeException("boom"))
                .when(gitGateway).streamCommits(any(), any(), any(), any());

        CollectionRun result = service.run(SINCE);

        assertAll("git упал",
                () -> assertThat(result.status()).isEqualTo(CollectionStatus.FAILED),
                () -> assertThat(result.error()).hasValue("boom"),
                () -> verify(kaitenGateway, never()).streamCards(any(), any(), any()),
                () -> verify(collectionRunRepository, times(2)).save(any()));
    }

    @Test
    @DisplayName("Падение Kaiten изолировано ⇒ git-статистика сохранена, прогон SUCCESS")
    void kaitenFailureDoesNotRollbackGitStats() {
        when(gitGateway.configuredRepos()).thenReturn(List.of(REPO));
        when(gitGateway.prepare(REPO)).thenReturn(REPO);
        stubStreamCommits(List.of(commit(SHA_NEW)));
        when(commitRepository.findExistingHashes(anyCollection())).thenReturn(Set.of());
        when(unifiedUserRepository.findOrCreateAll(anyCollection()))
                .thenReturn(Map.of(AUTHOR, 1L));

        when(unifiedUserRepository.findAll()).thenReturn(List.of(userWithKaiten(1L, 9L)));
        doThrow(new RuntimeException("kaiten 429"))
                .when(kaitenGateway).streamCards(any(), any(), any());

        CollectionRun result = service.run(SINCE);

        assertAll("kaiten упал, git ок",
                () -> assertThat(result.status())
                        .as("прогон всё равно SUCCESS — Kaiten изолирован")
                        .isEqualTo(CollectionStatus.SUCCESS),
                () -> verify(commitRepository).saveAll(anyCollection()),
                () -> verify(dailyStatsRepository).upsertAll(anyCollection()),
                () -> verify(kaitenCardRepository, never()).upsertAll(anyCollection()));
    }

    @Test
    @DisplayName("since=null + есть последний SUCCESS ⇒ стартуем с lastUntil + 1 секунда")
    void resolveSinceFromLastSuccess() {
        LocalDateTime lastUntil = LocalDateTime.of(2026, 5, 20, 10, 0);
        when(collectionRunRepository.findLastSuccessfulUntil()).thenReturn(Optional.of(lastUntil));
        when(gitGateway.configuredRepos()).thenReturn(List.of());
        when(unifiedUserRepository.findAll()).thenReturn(List.of());

        service.run(null);

        ArgumentCaptor<CollectionRun> runs = ArgumentCaptor.forClass(CollectionRun.class);
        verify(collectionRunRepository, atLeastOnce()).save(runs.capture());

        assertThat(runs.getAllValues().get(0).sinceDate())
                .as("стартовали ровно с lastUntil + 1 сек")
                .isEqualTo(lastUntil.plusSeconds(1));
    }

    @Test
    @DisplayName("Нет пользователей с kaiten_id ⇒ карточки не зовём, но прогон SUCCESS")
    void skipsKaitenWhenNoMemberIds() {
        when(gitGateway.configuredRepos()).thenReturn(List.of());
        when(unifiedUserRepository.findAll()).thenReturn(List.of(userWithoutKaiten(1L)));

        CollectionRun result = service.run(SINCE);

        assertAll("kaiten пропущен по отсутствию member ids",
                () -> assertThat(result.status()).isEqualTo(CollectionStatus.SUCCESS),
                () -> verify(kaitenGateway, never()).streamCards(any(), any(), any()));
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

    private void stubStreamCards(List<KaitenCard> page) {
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Consumer<List<KaitenCard>> handler = inv.getArgument(2);
            handler.accept(page);
            return null;
        }).when(kaitenGateway).streamCards(any(), any(), any());
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

    private static UnifiedUser userWithKaiten(long id, long kaitenId) {
        LocalDateTime now = LocalDateTime.now();
        return new UnifiedUser(id, AUTHOR, "boris", "Boris", null,
                new KaitenUserId(kaitenId), null, now, now, now);
    }

    private static UnifiedUser userWithoutKaiten(long id) {
        LocalDateTime now = LocalDateTime.now();
        return new UnifiedUser(id, AUTHOR, "boris", "Boris", null,
                null, null, now, now, now);
    }

    private static KaitenCard card(long id) {
        return new KaitenCard(
                new ru.x5.markable.dev.analytics.domain.model.kaiten.KaitenCardId(id),
                "title " + id, "desc", "active",
                "Backlog", "Board", "Space",
                null, null,
                LocalDateTime.now(), LocalDateTime.now(), null,
                false,
                "https://kaiten.x5.ru/" + id,
                List.of());
    }
}
