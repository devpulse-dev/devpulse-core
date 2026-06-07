package ru.x5.devpulse.application.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 *   <li>{@code runMark = now()} — метка начала сбора репозитория;</li>
 *   <li>Стрим git коммитов → {@code persistCommitBatch} per-batch (своя короткая tx):
 *       existing-коммиты помечаются {@code markSeen(runMark)}, новые — сохраняются с {@code now()};</li>
 *   <li>Аккумуляция только {@code repoAffected} (email'ы авторов) — хеши в heap НЕ копим;</li>
 *   <li>Финальная атомарная tx ({@link TransactionRunner}):
 *       {@code deleteZombies(repo, period, runMark)} (sweep: всё, что не увидено в этом сборе) +
 *       recompute daily_stats. Либо оба применены, либо ни одного.</li>
 * </ol>
 *
 * <p><b>Память O(1) от размера репозитория:</b> zombie-cleanup — это mark-and-sweep по
 * {@code collected_at} в БД, а не set-разность в heap. На репо с миллионом коммитов мы не
 * держим миллион хешей в памяти (ни {@code seenInGit}, ни полную выгрузку из БД). См. P0-2.</p>
 *
 * <p><b>Семантика отказа:</b> repo N упал → repos 1..N-1 консистентны (предыдущие финальные tx
 * прошли). Repo N — либо в исходном состоянии (финальная tx откатилась), либо batches уже
 * сохранены, но sweep+recompute ещё не применён. Retry идемпотентен: дубли отсекаются
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

        // Метка сбора: всё, что увидено в этом прогоне (insert либо markSeen), получит
        // collected_at >= runMark; всё, что осталось со старым collected_at — зомби.
        LocalDateTime runMark = LocalDateTime.now();
        Set<Email> repoAffected = new HashSet<>();

        gitGateway.streamCommits(repo, since, until, batch -> {
            for (Commit c : batch) {
                if (c.authorEmail() != null) repoAffected.add(c.authorEmail());
            }
            persistCommitBatch(batch, runMark);
        });

        // Финальная атомарная tx: sweep zombies + recompute видимы вместе или никак.
        // Supplier-перегрузка (с return null) — Runnable-overload default-метод в интерфейсе,
        // mock-фреймворки его не вызывают.
        transactionRunner.inTransaction(() -> {
            commitRepository.deleteZombies(repo, period, runMark);
            if (!repoAffected.isEmpty()) {
                dailyStatsRepository.recomputeFromCommits(repoAffected, period);
            }
            return null;
        });

        return repoAffected;
    }

    /**
     * Один батч коммитов: дедуп по hash → existing помечаем «увиден» (markSeen) →
     * find-or-create users → save новых. {@code runMark} — общая метка прогона.
     */
    private void persistCommitBatch(List<Commit> batch, LocalDateTime runMark) {
        if (batch == null || batch.isEmpty()) return;

        Set<CommitHash> hashes = new HashSet<>(batch.size());
        for (Commit c : batch) hashes.add(c.hash());

        Set<CommitHash> existing = commitRepository.findExistingHashes(hashes);

        // Existing-коммиты всё ещё в git — защищаем от sweep'а (bump collected_at до runMark).
        // Делаем ДО early-return: батч может целиком состоять из existing-коммитов, и их тоже
        // нужно пометить увиденными, иначе deleteZombies снесёт их как зомби.
        if (!existing.isEmpty()) {
            commitRepository.markSeen(existing, runMark);
        }

        List<Commit> fresh = new ArrayList<>(batch.size());
        for (Commit c : batch) {
            if (!existing.contains(c.hash())) fresh.add(c);
        }

        if (fresh.isEmpty()) {
            log.debug("Батч из {} коммитов уже в БД — помечены увиденными, новых нет", batch.size());
            return;
        }

        // batch find-or-create — обеспечивает наличие записи в unified_user (FK для commit_details).
        // Identity-резолв — обязанность use case'а; адаптер saveAll получает готовый user_id-маппинг.
        Set<Email> emails = new HashSet<>(fresh.size());
        for (Commit c : fresh) {
            if (c.authorEmail() != null) emails.add(c.authorEmail());
        }
        Map<Email, Long> userByEmail = unifiedUserRepository.findOrCreateAll(emails);

        commitRepository.saveAll(fresh, userByEmail);
        log.info("Батч сохранён: {} новых коммитов (из {} в батче)", fresh.size(), batch.size());
    }
}
