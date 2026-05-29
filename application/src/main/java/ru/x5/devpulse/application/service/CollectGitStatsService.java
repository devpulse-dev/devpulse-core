package ru.x5.devpulse.application.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.x5.devpulse.application.port.in.CollectGitStatsUseCase;
import ru.x5.devpulse.application.port.out.CommitRepository;
import ru.x5.devpulse.application.port.out.DailyStatsRepository;
import ru.x5.devpulse.application.port.out.GitGateway;
import ru.x5.devpulse.application.port.out.TransactionRunner;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.git.CommitHash;
import ru.x5.devpulse.domain.model.git.RepoName;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Реализация {@link CollectGitStatsUseCase}.
 *
 * <p><b>Пайплайн (per-repo):</b></p>
 * <ol>
 *   <li>Стрим git коммитов → {@code persistCommitBatch} per-batch (своя короткая tx);</li>
 *   <li>Аккумуляция: {@code seenInGit} (хеши) + {@code repoAffected} (email'ы авторов);</li>
 *   <li>Финальная атомарная tx ({@link TransactionRunner}):
 *       cleanup zombies ({@code findHashesByRepoAndPeriod} ∖ {@code seenInGit}) + recompute
 *       daily_stats. Либо оба применены, либо ни одного.</li>
 * </ol>
 *
 * <p><b>Семантика отказа:</b> repo N упал → repos 1..N-1 консистентны (предыдущие финальные tx
 * прошли). Repo N — либо в исходном состоянии (финальная tx откатилась), либо batches уже
 * сохранены, но cleanup+recompute ещё не применён. Retry идемпотентен: дубли отсекаются
 * {@code findExistingHashes}, recompute сделает всё заново.</p>
 */
@Slf4j
@RequiredArgsConstructor
public final class CollectGitStatsService implements CollectGitStatsUseCase {

    private final GitGateway gitGateway;
    private final CommitRepository commitRepository;
    private final DailyStatsRepository dailyStatsRepository;
    private final UnifiedUserRepository unifiedUserRepository;
    private final TransactionRunner transactionRunner;

    @Override
    public Set<Email> collect(LocalDateTime since, LocalDateTime until) {
        List<RepoName> repos = gitGateway.configuredRepos();
        log.info("Сбор по {} репозиториям", repos.size());

        Period period = new Period(since.toLocalDate(), until.toLocalDate());
        Set<Email> allAffected = new HashSet<>();

        for (RepoName repo : repos) {
            allAffected.addAll(collectOneRepo(repo, since, until, period));
        }

        return allAffected;
    }

    private Set<Email> collectOneRepo(RepoName repo,
                                      LocalDateTime since,
                                      LocalDateTime until,
                                      Period period) {
        log.info("Стримим коммиты из {}", repo.value());

        Set<CommitHash> seenInGit = new HashSet<>();
        Set<Email> repoAffected = new HashSet<>();

        gitGateway.streamCommits(repo, since, until, batch -> {
            for (Commit c : batch) {
                seenInGit.add(c.hash());
                if (c.authorEmail() != null) repoAffected.add(c.authorEmail());
            }
            persistCommitBatch(batch);
        });

        // Финальная атомарная tx: cleanup + recompute видимы вместе или никак.
        // Supplier-перегрузка (с return null) — Runnable-overload default-метод в интерфейсе,
        // mock-фреймворки его не вызывают.
        transactionRunner.inTransaction(() -> {
            Set<CommitHash> inDb = commitRepository.findHashesByRepoAndPeriod(repo, period);
            Set<CommitHash> zombies = new HashSet<>(inDb);
            zombies.removeAll(seenInGit);
            if (!zombies.isEmpty()) {
                log.info("Repo {}: {} zombie-коммитов (rebase/force-push) — удаляем",
                        repo.value(), zombies.size());
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

        // batch find-or-create — обеспечивает наличие записи в unified_user (FK для commit_details).
        Set<Email> emails = new HashSet<>(fresh.size());
        for (Commit c : fresh) {
            if (c.authorEmail() != null) emails.add(c.authorEmail());
        }
        unifiedUserRepository.findOrCreateAll(emails);

        commitRepository.saveAll(fresh);
        log.info("Батч сохранён: {} новых коммитов (из {} в батче)", fresh.size(), batch.size());
    }
}
