package ru.x5.devpulse.application.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.x5.devpulse.application.port.in.CancellationSignal;
import ru.x5.devpulse.application.port.in.CollectDailyStatsUseCase;
import ru.x5.devpulse.application.port.in.CollectGitStatsUseCase;
import ru.x5.devpulse.application.port.in.CollectReviewsUseCase;
import ru.x5.devpulse.application.port.in.SyncKaitenUsersUseCase;
import ru.x5.devpulse.application.port.out.CollectionLock;
import ru.x5.devpulse.application.port.out.CollectionRunRepository;
import ru.x5.devpulse.application.port.out.KaitenCardsCache;
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
    private final CollectReviewsUseCase collectReviews;
    private final CollectionRunRepository collectionRunRepository;
    private final CollectionLock collectionLock;
    private final KaitenCardsCache kaitenCardsCache;

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

        // Сигнал отмены, backed by флагом в БД (кросс-инстансно). Проверяется git-фазой между
        // репозиториями и оркестратором между фазами.
        CancellationSignal cancel = () -> collectionRunRepository.isCancelRequested(run.id());

        Set<Email> affected = new HashSet<>();
        try {
            affected = collectGitStats.collect(effectiveSince, until, cancel);
        } catch (CollectionCancelledException ce) {
            return finishCancelled(run, ce.getMessage());
        } catch (Exception e) {
            log.error("Git-фаза упала — фиксируем FAILED, Kaiten пропускаем", e);
            CollectionRun failed = run.failed(e.getMessage());
            collectionRunRepository.save(failed);
            return failed;
        }

        // Checkpoint после git: отмена могла прийти, пока git дорабатывал последний репо.
        if (cancel.cancelled()) {
            return finishCancelled(run, "Сбор отменён после git-фазы");
        }

        // Kaiten — изолированно. Падение не откатывает git stats.
        try {
            syncKaitenUsers.syncAll();
        } catch (Exception e) {
            log.error("Sync пользователей Kaiten упал (git-статистика уже сохранена): {}",
                    e.getMessage(), e);
        }

        // Ревью-метрики из GitLab — изолированно. Падение/недоступность GitLab не валит прогон.
        try {
            collectReviews.collect(effectiveSince);
        } catch (Exception e) {
            log.error("Сбор ревью-метрик из GitLab упал (остальные фазы уже сохранены): {}",
                    e.getMessage(), e);
        }

        // Checkpoint перед фиксацией SUCCESS: если отмену запросили во время kaiten/reviews —
        // честно помечаем CANCELLED (данные собраны, но оператор просил остановиться; следующий
        // прогон идемпотентно пересоберёт от того же since).
        if (cancel.cancelled()) {
            return finishCancelled(run, "Сбор отменён после git/kaiten/reviews");
        }

        // Сбор успешен → инвалидируем кэш карточек Kaiten, чтобы фронт сразу видел свежие
        // данные на /profile, не ждал истечения TTL (5 минут — слишком долго после явного сбора).
        try {
            kaitenCardsCache.invalidateAll();
        } catch (Exception e) {
            // Падение invalidate не должно валить уже-успешный сбор — кэш сам истечёт по TTL.
            log.warn("Не удалось очистить кэш Kaiten cards: {}", e.getMessage());
        }

        CollectionRun ok = run.succeeded();
        collectionRunRepository.save(ok);
        log.info("Сбор {} успешно завершён (затронуто авторов: {})", ok.id(), affected.size());
        return ok;
    }

    /** Фиксирует прогон как CANCELLED. daily_stats может быть неполным — доберёт следующий сбор. */
    private CollectionRun finishCancelled(CollectionRun run, String reason) {
        log.info("Сбор {} отменён: {}", run.id(), reason);
        CollectionRun cancelled = run.cancelled(reason);
        collectionRunRepository.save(cancelled);
        return cancelled;
    }

    private LocalDateTime resolveSince(LocalDateTime explicit) {
        if (explicit != null) return explicit;
        return collectionRunRepository.findLastSuccessfulUntil()
                .map(t -> t.plusSeconds(1))
                .orElse(DEFAULT_START_DATE);
    }
}
