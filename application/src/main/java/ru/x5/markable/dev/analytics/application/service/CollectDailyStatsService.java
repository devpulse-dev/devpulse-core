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
import ru.x5.markable.dev.analytics.application.port.out.KaitenCardRepository;
import ru.x5.markable.dev.analytics.application.port.out.KaitenGateway;
import ru.x5.markable.dev.analytics.application.port.out.UnifiedUserRepository;
import ru.x5.markable.dev.analytics.domain.model.collection.CollectionRun;
import ru.x5.markable.dev.analytics.domain.model.git.Commit;
import ru.x5.markable.dev.analytics.domain.model.git.CommitHash;
import ru.x5.markable.dev.analytics.domain.model.git.RepoName;
import ru.x5.markable.dev.analytics.domain.model.kaiten.KaitenCard;
import ru.x5.markable.dev.analytics.domain.model.stats.DailyAuthorStats;
import ru.x5.markable.dev.analytics.domain.model.user.Email;
import ru.x5.markable.dev.analytics.domain.model.user.KaitenUserId;
import ru.x5.markable.dev.analytics.domain.model.user.UnifiedUser;
import ru.x5.markable.dev.analytics.domain.service.AuthorAggregator;

/**
 * Реализация {@link CollectDailyStatsUseCase}.
 *
 * <p>Оркестрация полного цикла сбора: Git → CommitRepository + DailyStatsRepository →
 * (изолированно) Kaiten → KaitenCardRepository, плюс журнал в {@link CollectionRun}.</p>
 *
 * <p><b>Контракт двух фаз:</b> ошибка Kaiten НЕ откатывает уже сохранённые git-статы.
 * Это намеренно: rate-limit Kaiten не должен терять часы git-сбора. Поведение совпадает
 * со старой реализацией {@code DailyStatsServiceImpl.collectStatsForPeriod}.</p>
 *
 * <p><b>Транзакции:</b> use case намеренно НЕ транзакционен. Каждый bulk-upsert (commits,
 * daily stats, kaiten cards) — отдельный батч с собственной транзакцией внутри адаптера.
 * Так уже сохранённый прогресс не теряется при падении на поздних страницах.</p>
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
    private final KaitenCardRepository kaitenCardRepository;
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
            log.error("Сбор git-статистики упал — фиксируем FAILED, Kaiten пропускаем", e);
            CollectionRun failed = run.failed(e.getMessage());
            collectionRunRepository.save(failed);
            return failed;
        }

        // Kaiten — изолированный шаг: его падение не откатывает уже сохранённые git stats.
        try {
            collectKaitenCards(effectiveSince);
        } catch (Exception e) {
            log.error("Сбор карточек Kaiten упал (git-статистика уже сохранена): {}", e.getMessage(), e);
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

    /* ------------ kaiten phase ------------ */

    private void collectKaitenCards(LocalDateTime since) {
        List<UnifiedUser> users = unifiedUserRepository.findAll();
        List<KaitenUserId> memberIds = new ArrayList<>();
        for (UnifiedUser u : users) {
            u.kaiten().ifPresent(memberIds::add);
        }

        if (memberIds.isEmpty()) {
            log.warn("Нет пользователей с kaiten_id — пропускаем сбор карточек");
            return;
        }

        log.info("Стримим карточки Kaiten для {} пользователей с {}", memberIds.size(), since);
        kaitenGateway.streamCards(memberIds, since, this::persistCardPage);
    }

    private void persistCardPage(List<KaitenCard> page) {
        if (page == null || page.isEmpty()) return;
        kaitenCardRepository.upsertAll(page);
        log.info("Сохранили страницу карточек Kaiten: {}", page.size());
    }

    /* ------------ helpers ------------ */

    private LocalDateTime resolveSince(LocalDateTime explicit) {
        if (explicit != null) return explicit;
        return collectionRunRepository.findLastSuccessfulUntil()
                .map(t -> t.plusSeconds(1))
                .orElse(DEFAULT_START_DATE);
    }
}
