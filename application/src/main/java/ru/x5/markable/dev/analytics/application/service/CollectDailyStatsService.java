package ru.x5.markable.dev.analytics.application.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.x5.markable.dev.analytics.application.port.in.CollectDailyStatsUseCase;
import ru.x5.markable.dev.analytics.application.port.out.CollectionRunRepository;
import ru.x5.markable.dev.analytics.application.port.out.CommitRepository;
import ru.x5.markable.dev.analytics.application.port.out.DailyStatsRepository;
import ru.x5.markable.dev.analytics.application.port.out.GitGateway;
import ru.x5.markable.dev.analytics.application.port.out.KaitenGateway;
import ru.x5.markable.dev.analytics.application.port.out.KaitenUserRepository;
import ru.x5.markable.dev.analytics.application.port.out.UnifiedUserRepository;
import ru.x5.markable.dev.analytics.domain.model.collection.CollectionRun;
import ru.x5.markable.dev.analytics.domain.model.git.Commit;
import ru.x5.markable.dev.analytics.domain.model.git.CommitHash;
import ru.x5.markable.dev.analytics.domain.model.git.RepoName;
import ru.x5.markable.dev.analytics.domain.model.kaiten.KaitenUser;
import ru.x5.markable.dev.analytics.domain.model.stats.DailyAuthorStats;
import ru.x5.markable.dev.analytics.domain.model.user.Email;
import ru.x5.markable.dev.analytics.domain.service.AuthorAggregator;

/**
 * Реализация {@link CollectDailyStatsUseCase}.
 *
 * <p>Оркестрация полного цикла сбора:
 * <ol>
 *   <li>Git → CommitRepository + DailyStatsRepository (новые коммиты и daily-агрегаты)</li>
 *   <li>(изолированно) Kaiten users sync — обновляем {@code kaiten_user} и проставляем
 *       {@code kaiten_id}/{@code avatar_url} в {@code unified_user} по совпадению email.</li>
 * </ol>
 *
 * <p><b>Что НЕ делает:</b> карточки Kaiten в этом сценарии не выкачиваются — это намеренно.
 * Карточки нужны только в разрезе профиля одного пользователя и тянутся live при запросе
 * {@code GET /api/v2/users/{email}/profile}. Так мы не тратим бюджет Kaiten-API на
 * массовый pre-fetch, который часто никто не смотрит.</p>
 *
 * <p><b>Контракт двух фаз:</b> ошибка Kaiten sync НЕ откатывает уже сохранённые git-статы.
 * Это намеренно: rate-limit Kaiten не должен терять часы git-сбора.</p>
 *
 * <p><b>Транзакции:</b> use case намеренно НЕ транзакционен. Каждый bulk-upsert (commits,
 * daily stats, kaiten users) — отдельный батч с собственной транзакцией внутри адаптера.</p>
 */
@Slf4j
@RequiredArgsConstructor
public final class CollectDailyStatsService implements CollectDailyStatsUseCase {

    /** Точка начала истории, если ни одного успешного сбора ещё не было. */
    private static final LocalDateTime DEFAULT_START_DATE = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

    private final GitGateway gitGateway;
    private final KaitenGateway kaitenGateway;
    private final CommitRepository commitRepository;
    private final DailyStatsRepository dailyStatsRepository;
    private final KaitenUserRepository kaitenUserRepository;
    private final UnifiedUserRepository unifiedUserRepository;
    private final CollectionRunRepository collectionRunRepository;

    @Override
    public CollectionRun run(LocalDateTime since) {
        LocalDateTime effectiveSince = resolveSince(since);
        LocalDateTime until = LocalDateTime.now();

        if (!effectiveSince.isBefore(until)) {
            log.info("Нечего собирать: since={} >= until={}", effectiveSince, until);
            CollectionRun empty = CollectionRun.start(effectiveSince, until).succeeded();
            collectionRunRepository.save(empty);
            return empty;
        }

        CollectionRun run = CollectionRun.start(effectiveSince, until);
        collectionRunRepository.save(run);
        log.info("Старт сбора {} ({} → {})", run.id(), effectiveSince, until);

        try {
            collectGitStats(effectiveSince, until);
        } catch (Exception e) {
            log.error("Сбор git-статистики упал — фиксируем FAILED, Kaiten users пропускаем", e);
            CollectionRun failed = run.failed(e.getMessage());
            collectionRunRepository.save(failed);
            return failed;
        }

        // Kaiten users sync — изолированный шаг: его падение не откатывает уже сохранённые git stats.
        try {
            syncKaitenUsers();
        } catch (Exception e) {
            log.error("Sync пользователей Kaiten упал (git-статистика уже сохранена): {}", e.getMessage(), e);
        }

        CollectionRun ok = run.succeeded();
        collectionRunRepository.save(ok);
        log.info("Сбор {} успешно завершён", ok.id());
        return ok;
    }

    /* ------------ git phase ------------ */

    private void collectGitStats(LocalDateTime since, LocalDateTime until) {
        List<RepoName> repos = gitGateway.configuredRepos();
        log.info("Сбор по {} репозиториям", repos.size());

        for (RepoName repo : repos) {
            RepoName prepared = gitGateway.prepare(repo);
            log.info("Стримим коммиты из {}", prepared.value());
            gitGateway.streamCommits(prepared, since, until, this::persistCommitBatch);
        }
    }

    /**
     * Один батч коммитов: дедуп по hash → batch find-or-create users → save commits →
     * агрегация в daily stats → upsert daily stats.
     */
    private void persistCommitBatch(List<Commit> batch) {
        if (batch == null || batch.isEmpty()) return;

        Set<CommitHash> hashes = new HashSet<>(batch.size());
        for (Commit c : batch) hashes.add(c.hash());

        Set<CommitHash> existing = commitRepository.findExistingHashes(hashes);

        List<Commit> fresh = new ArrayList<>(batch.size());
        for (Commit c : batch) {
            if (!existing.contains(c.hash())) fresh.add(c);
        }

        if (fresh.isEmpty()) {
            log.debug("Батч из {} коммитов уже в БД — пропуск", batch.size());
            return;
        }

        // batch find-or-create users — одной операцией, без N+1
        Set<Email> emails = new HashSet<>(fresh.size());
        for (Commit c : fresh) {
            if (c.authorEmail() != null) emails.add(c.authorEmail());
        }
        Map<Email, Long> userIdByEmail = unifiedUserRepository.findOrCreateAll(emails);

        commitRepository.saveAll(fresh);

        // Чистая агрегация в domain → проставляем userId → upsert
        List<DailyAuthorStats> aggregated = AuthorAggregator.aggregateByDay(fresh);
        List<DailyAuthorStats> withUsers = new ArrayList<>(aggregated.size());
        for (DailyAuthorStats s : aggregated) {
            withUsers.add(s.withUserId(userIdByEmail.get(s.authorEmail())));
        }
        dailyStatsRepository.upsertAll(withUsers);

        log.info("Батч сохранён: {} новых коммитов (из {} в батче), {} дневных агрегатов",
                fresh.size(), batch.size(), withUsers.size());
    }

    /* ------------ kaiten users sync phase ------------ */

    /**
     * Обновляет зеркало пользователей Kaiten в локальной БД и подтягивает
     * {@code kaiten_id} + {@code avatar_url} в {@code unified_user} по совпадению email.
     */
    private void syncKaitenUsers() {
        List<KaitenUser> users = kaitenGateway.fetchAllUsers();
        if (users.isEmpty()) {
            log.warn("Kaiten вернул пустой список пользователей — sync пропущен");
            return;
        }

        kaitenUserRepository.upsertAll(users);

        int linked = 0;
        for (KaitenUser u : users) {
            Email email = u.email();
            if (email == null) continue;
            unifiedUserRepository.updateKaitenId(email, u.id(), u.fullName(), u.avatarUrl());
            linked++;
        }
        log.info("Sync Kaiten users: upserted {}, привязано к unified_user {}", users.size(), linked);
    }

    /* ------------ helpers ------------ */

    private LocalDateTime resolveSince(LocalDateTime explicit) {
        if (explicit != null) return explicit;
        return collectionRunRepository.findLastSuccessfulUntil()
                .map(t -> t.plusSeconds(1))
                .orElse(DEFAULT_START_DATE);
    }
}
