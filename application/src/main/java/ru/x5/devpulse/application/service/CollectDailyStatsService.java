package ru.x5.devpulse.application.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.x5.devpulse.application.port.in.CollectDailyStatsUseCase;
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
import ru.x5.devpulse.domain.model.collection.CollectionRun;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.git.CommitHash;
import ru.x5.devpulse.domain.model.git.RepoName;
import ru.x5.devpulse.domain.model.kaiten.KaitenUser;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Реализация {@link CollectDailyStatsUseCase}.
 *
 * <p><b>Пайплайн сбора (новая версия — устойчива к rebase/force-push):</b></p>
 * <ol>
 *   <li><b>Per-repo:</b> стримим git-коммиты за период. Каждый батч сохраняем в
 *       {@code commit_details} (дедуп по hash). Параллельно копим {@code Set} всех увиденных
 *       хешей этого репо.</li>
 *   <li><b>Cleanup zombies:</b> после окончания стрима — берём из БД ВСЕ хеши репо за
 *       тот же период и удаляем те, которых в этом сборе не было (rebase / force-push
 *       выкинул их из ветки).</li>
 *   <li><b>Recompute daily_stats:</b> один раз в конце git-фазы для всех затронутых
 *       (email, period) пересчитываем daily-агрегаты из текущего {@code commit_details} —
 *       это убирает рассинхрон, который раньше копился из-за инкрементальных UPSERT'ов,
 *       перезаписывающих значения вместо суммирования.</li>
 *   <li><b>Kaiten users sync</b> (изолированно): {@code kaitenGateway.fetchAllUsers()} →
 *       upsert в {@code kaiten_user} + проставить {@code kaiten_id}/{@code avatar_url}
 *       в {@code unified_user} по совпадению email.</li>
 * </ol>
 *
 * <p><b>Изоляция фаз:</b> ошибка Kaiten НЕ откатывает git-статы.</p>
 *
 * <p><b>Транзакционные границы:</b></p>
 * <ul>
 *   <li>Каждый {@code saveAll(batch)} — своя короткая tx (через {@code @Transactional}
 *       на adapter методе). Это держит tx максимально короткой во время длинного git log.</li>
 *   <li>Финальные шаги cleanup zombies + recompute daily_stats для одного репо —
 *       <b>одна общая tx</b> через {@link TransactionRunner}. Это гарантирует:
 *       либо для репо обновлены и {@code commit_details}, и {@code daily_stats},
 *       либо состояние репо в БД не тронуто (rollback при падении).</li>
 *   <li>Если падение случилось в середине стрима — {@code saveAll}-батчи уже закоммичены,
 *       но {@code lastSuccessfulUntil} не обновляется (run = FAILED). Следующий запуск
 *       retry'нет с того же since, дубли отсекаются {@code findExistingHashes}, и cleanup
 *       +recompute доедет до конца. <b>Идемпотентно.</b></li>
 * </ul>
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
    private final CollectionLock collectionLock;
    private final TransactionRunner transactionRunner;

    /**
     * Запуск сбора.
     *
     * <p><b>Конкуренция:</b> метод защищён distributed lock'ом ({@link CollectionLock}).
     * Если другой сбор уже идёт, бросается {@link
     * ru.x5.devpulse.application.port.out.CollectionAlreadyRunningException}. Lock берётся
     * ДО создания CollectionRun — иначе в БД остался бы artefact 'STARTED' с unfinished
     * статусом, который бы дезинформировал {@code resolveSince()}.</p>
     */
    @Override
    public CollectionRun run(LocalDateTime since) {
        try (CollectionLock.Handle ignored = collectionLock.acquireOrThrow()) {
            return doRun(since);
        }
    }

    private CollectionRun doRun(LocalDateTime since) {
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

        Set<Email> affectedAuthors = new HashSet<>();
        try {
            affectedAuthors = collectGitStats(effectiveSince, until);
        } catch (Exception e) {
            log.error("Сбор git-статистики упал — фиксируем FAILED, Kaiten пропускаем", e);
            CollectionRun failed = run.failed(e.getMessage());
            collectionRunRepository.save(failed);
            return failed;
        }

        // Kaiten users sync — изолированно.
        try {
            syncKaitenUsers();
        } catch (Exception e) {
            log.error("Sync пользователей Kaiten упал (git-статистика уже сохранена): {}",
                    e.getMessage(), e);
        }

        CollectionRun ok = run.succeeded();
        collectionRunRepository.save(ok);
        log.info("Сбор {} успешно завершён (затронуто авторов: {})", ok.id(), affectedAuthors.size());
        return ok;
    }

    /* ------------ git phase ------------ */

    /**
     * Сбор по всем репозиториям + cleanup zombies + recompute daily_stats.
     *
     * @return множество email-ов всех авторов, чьи коммиты затронуты в этом сборе
     *         (включая авторов удалённых zombie-коммитов)
     */
    private Set<Email> collectGitStats(LocalDateTime since, LocalDateTime until) {
        List<RepoName> repos = gitGateway.configuredRepos();
        log.info("Сбор по {} репозиториям", repos.size());

        Period period = new Period(since.toLocalDate(), until.toLocalDate());
        Set<Email> allAffected = new HashSet<>();

        for (RepoName repo : repos) {
            allAffected.addAll(collectOneRepo(repo, since, until, period));
        }

        return allAffected;
    }

    /**
     * Один репозиторий: stream коммитов + per-batch save (каждый — своя tx) → финальная
     * atomic tx { cleanup zombies + recompute daily_stats для этого репо }.
     *
     * <p><b>Почему recompute per-repo, а не один в конце:</b> атомарность важна на уровне
     * репозитория — если падение случится между repo N и repo N+1, первые N репо консистентны.
     * Если бы recompute был общим в конце — частичный сбор оставлял бы commit_details без
     * актуального daily_stats до следующего успешного полного сбора.</p>
     */
    private Set<Email> collectOneRepo(RepoName repo,
                                      LocalDateTime since,
                                      LocalDateTime until,
                                      Period period) {
        RepoName prepared = gitGateway.prepare(repo);
        log.info("Стримим коммиты из {}", prepared.value());

        // Накапливаем хеши и authors реально пришедшие из git.
        // gitGateway.streamCommits отдаёт batch'и из reader-thread'а; persistCommitBatch
        // делает свой @Transactional saveAll — короткая tx на каждый батч.
        Set<CommitHash> seenInGit = new HashSet<>();
        Set<Email> repoAffected = new HashSet<>();

        gitGateway.streamCommits(prepared, since, until, batch -> {
            for (Commit c : batch) {
                seenInGit.add(c.hash());
                if (c.authorEmail() != null) repoAffected.add(c.authorEmail());
            }
            persistCommitBatch(batch);
        });

        // Финальная атомарная tx: cleanup + recompute видимы вместе или никак.
        // Используем Supplier-перегрузку (с явным return null) — Runnable-overload в интерфейсе
        // default, и mock-фреймворки его по умолчанию не вызывают.
        transactionRunner.inTransaction(() -> {
            Set<CommitHash> inDb = commitRepository.findHashesByRepoAndPeriod(prepared, period);
            Set<CommitHash> zombies = new HashSet<>(inDb);
            zombies.removeAll(seenInGit);
            if (!zombies.isEmpty()) {
                log.info("Repo {}: {} zombie-коммитов (rebase/force-push) — удаляем",
                        prepared.value(), zombies.size());
                commitRepository.deleteByHashes(zombies);
            }
            if (!repoAffected.isEmpty()) {
                dailyStatsRepository.recomputeFromCommits(repoAffected, period);
            }
            return null;
        });

        return repoAffected;
    }

    /**
     * Один батч коммитов: дедуп по hash → batch find-or-create users → save commits.
     *
     * <p>В отличие от прежней версии — daily_stats тут НЕ обновляем. Пересчёт делается
     * один раз в конце git-фазы из текущего {@code commit_details}.</p>
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

        // batch find-or-create — обеспечивает наличие записи в unified_user
        // (нужно для FK при сохранении commit_details).
        Set<Email> emails = new HashSet<>(fresh.size());
        for (Commit c : fresh) {
            if (c.authorEmail() != null) emails.add(c.authorEmail());
        }
        unifiedUserRepository.findOrCreateAll(emails);

        commitRepository.saveAll(fresh);
        log.info("Батч сохранён: {} новых коммитов (из {} в батче)", fresh.size(), batch.size());
    }

    /* ------------ kaiten users sync phase ------------ */

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
