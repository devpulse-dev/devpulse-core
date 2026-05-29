package ru.x5.devpulse.application.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.x5.devpulse.application.port.in.CollectDailyStatsUseCase;
import ru.x5.devpulse.application.port.in.CollectGitStatsUseCase;
import ru.x5.devpulse.application.port.in.SyncKaitenUsersUseCase;
import ru.x5.devpulse.application.port.out.CollectionLock;
import ru.x5.devpulse.application.port.out.CollectionRunRepository;
import ru.x5.devpulse.domain.model.collection.CollectionRun;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Orchestrator-реализация {@link CollectDailyStatsUseCase}.
 *
 * <p><b>Что этот класс делает:</b></p>
 * <ol>
 *   <li>Берёт distributed lock ({@link CollectionLock}) — другой сбор не стартанёт параллельно.</li>
 *   <li>Резолвит {@code since} (либо явный, либо {@code lastSuccessfulUntil + 1s}).</li>
 *   <li>Создаёт {@code CollectionRun} со статусом STARTED.</li>
 *   <li>Делегирует git-фазу {@link CollectGitStatsUseCase}; падение → run = FAILED, kaiten не зовём.</li>
 *   <li>Делегирует kaiten-синк {@link SyncKaitenUsersUseCase}; падение изолировано (git stats остаются).</li>
 *   <li>Фиксирует {@code CollectionRun} как SUCCESS / FAILED.</li>
 * </ol>
 *
 * <p><b>Что этот класс НЕ делает:</b> ни одного выхода в I/O напрямую (нет {@code @Transactional}-методов,
 * нет {@code GitGateway}/{@code KaitenGateway}). Только координация двух use case'ов и
 * lifecycle CollectionRun. Это decomposition после review-фикса #15 — раньше тут было 8 зависимостей
 * и три не связанных дела.</p>
 *
 * <p><b>Public API контракт:</b> {@code POST /api/v2/collection/runs} продолжает работать как и
 * раньше — orchestrator реализует тот же port {@link CollectDailyStatsUseCase}.</p>
 */
@Slf4j
@RequiredArgsConstructor
public final class CollectDailyStatsService implements CollectDailyStatsUseCase {

    /** Точка начала истории, если ни одного успешного сбора ещё не было. */
    private static final LocalDateTime DEFAULT_START_DATE = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

    private final CollectGitStatsUseCase collectGitStats;
    private final SyncKaitenUsersUseCase syncKaitenUsers;
    private final CollectionRunRepository collectionRunRepository;
    private final CollectionLock collectionLock;

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

        Set<Email> affected = new HashSet<>();
        try {
            affected = collectGitStats.collect(effectiveSince, until);
        } catch (Exception e) {
            log.error("Git-фаза упала — фиксируем FAILED, Kaiten пропускаем", e);
            CollectionRun failed = run.failed(e.getMessage());
            collectionRunRepository.save(failed);
            return failed;
        }

        // Kaiten — изолированно. Падение не откатывает git stats.
        try {
            syncKaitenUsers.syncAll();
        } catch (Exception e) {
            log.error("Sync пользователей Kaiten упал (git-статистика уже сохранена): {}",
                    e.getMessage(), e);
        }

        CollectionRun ok = run.succeeded();
        collectionRunRepository.save(ok);
        log.info("Сбор {} успешно завершён (затронуто авторов: {})", ok.id(), affected.size());
        return ok;
    }

    private LocalDateTime resolveSince(LocalDateTime explicit) {
        if (explicit != null) return explicit;
        return collectionRunRepository.findLastSuccessfulUntil()
                .map(t -> t.plusSeconds(1))
                .orElse(DEFAULT_START_DATE);
    }
}
