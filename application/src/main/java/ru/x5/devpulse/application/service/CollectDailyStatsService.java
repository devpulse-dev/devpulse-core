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
import ru.x5.devpulse.application.port.out.BackgroundRunner;
import ru.x5.devpulse.application.port.out.CollectionLock;
import ru.x5.devpulse.application.port.out.CollectionRunRepository;
import ru.x5.devpulse.application.port.out.KaitenCardsCache;
import ru.x5.devpulse.domain.model.collection.CollectionRun;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Orchestrator-реализация {@link CollectDailyStatsUseCase}.
 *
 * <p><b>Асинхронный (POST → 202).</b> {@link #run} делает только синхронный пролог и возвращает
 * прогон в статусе RUNNING; сам сбор уходит в фон через {@link BackgroundRunner}.</p>
 * <ol>
 *   <li><b>Sync-пролог:</b> берёт distributed lock ({@link CollectionLock}) — 409 если занят;
 *       резолвит {@code since}; сохраняет {@code CollectionRun(RUNNING)}; диспатчит сбор в фон;
 *       возвращает RUNNING (контроллер отдаёт 202).</li>
 *   <li><b>Async-тело</b> ({@link #doRunBody}): git → kaiten → reviews → recompute; фиксирует
 *       терминальный статус (SUCCESS/FAILED/CANCELLED) и в самом конце отпускает advisory-lock.</li>
 * </ol>
 *
 * <p><b>Lock hand-off:</b> лок берётся синхронно (ради 409) и НЕ через try-with-resources — его
 * {@link CollectionLock.Handle} живёт до конца фоновой работы и закрывается в её {@code finally}.
 * Если диспатч не состоялся (пустой период / исключение в прологе) — лок отпускается синхронно.</p>
 *
 * <p><b>Отказоустойчивость:</b> любой неожиданный сбой фонового тела → прогон FAILED (ничего не
 * утекает в вечный RUNNING). Если процесс умрёт mid-run — прогон останется RUNNING и его подхватит
 * startup-реконсиляция (ADR-13).</p>
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
    private final BackgroundRunner backgroundRunner;

    @Override
    public CollectionRun run(LocalDateTime since) {
        // Лок берём синхронно (ради мгновенного 409) и держим до конца фоновой работы —
        // НЕ try-with-resources. Закрытие — в фоновом finally либо в нашем finally, если диспатч
        // не состоялся.
        CollectionLock.Handle handle = collectionLock.acquireOrThrow();
        boolean dispatched = false;
        try {
            LocalDateTime effectiveSince = resolveSince(since);
            LocalDateTime until = LocalDateTime.now();

            if (!effectiveSince.isBefore(until)) {
                log.info("Нечего собирать: since={} >= until={}", effectiveSince, until);
                CollectionRun empty = CollectionRun.start(effectiveSince, until).succeeded();
                collectionRunRepository.save(empty);
                return empty; // handle закроется в finally (dispatched=false)
            }

            CollectionRun run = CollectionRun.start(effectiveSince, until);
            collectionRunRepository.save(run);
            log.info("Старт сбора {} ({} → {}) — уходит в фон", run.id(), effectiveSince, until);

            backgroundRunner.run(() -> {
                try {
                    doRunBody(run, effectiveSince, until);
                } catch (Throwable t) {
                    // Ничего не должно утечь в вечный RUNNING — любой неожиданный сбой → FAILED.
                    log.error("Фоновый сбор {} упал неожиданно — фиксируем FAILED", run.id(), t);
                    safeSave(run.failed(String.valueOf(t.getMessage())));
                } finally {
                    handle.close(); // освобождаем advisory-lock после завершения сбора
                }
            });
            dispatched = true;
            return run; // RUNNING + id → контроллер отдаёт 202
        } finally {
            if (!dispatched) {
                handle.close(); // пустой период / исключение в прологе → не течём локом
            }
        }
    }

    /** Фоновое тело сбора: git → kaiten → reviews → recompute → терминальный статус. */
    private void doRunBody(CollectionRun run, LocalDateTime effectiveSince, LocalDateTime until) {
        // Сигнал отмены, backed by флагом в БД (кросс-инстансно). Проверяется git-фазой между
        // репозиториями и оркестратором между фазами.
        CancellationSignal cancel = () -> collectionRunRepository.isCancelRequested(run.id());

        Set<Email> affected = new HashSet<>();
        try {
            affected = collectGitStats.collect(effectiveSince, until, cancel);
        } catch (CollectionCancelledException ce) {
            finishCancelled(run, ce.getMessage());
            return;
        } catch (Exception e) {
            log.error("Git-фаза упала — фиксируем FAILED, Kaiten пропускаем", e);
            collectionRunRepository.save(run.failed(e.getMessage()));
            return;
        }

        // Checkpoint после git: отмена могла прийти, пока git дорабатывал последний репо.
        if (cancel.cancelled()) {
            finishCancelled(run, "Сбор отменён после git-фазы");
            return;
        }

        // Kaiten — изолированно. Падение не откатывает git stats.
        try {
            syncKaitenUsers.syncAll();
        } catch (Exception e) {
            log.error("Sync пользователей Kaiten упал (git-статистика уже сохранена): {}",
                    e.getMessage(), e);
        }

        // Ревью-метрики из GitLab — изолированно. Падение/недоступность GitLab не валит прогон.
        // cancel прокинут внутрь: отмена прекращает опрос новых проектов ревью.
        try {
            collectReviews.collect(effectiveSince, cancel);
        } catch (Exception e) {
            log.error("Сбор ревью-метрик из GitLab упал (остальные фазы уже сохранены): {}",
                    e.getMessage(), e);
        }

        // Checkpoint перед фиксацией SUCCESS: если отмену запросили во время kaiten/reviews —
        // честно помечаем CANCELLED (данные собраны, но оператор просил остановиться; следующий
        // прогон идемпотентно пересоберёт от того же since).
        if (cancel.cancelled()) {
            finishCancelled(run, "Сбор отменён после git/kaiten/reviews");
            return;
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
    }

    /** Фиксирует прогон как CANCELLED. daily_stats может быть неполным — доберёт следующий сбор. */
    private void finishCancelled(CollectionRun run, String reason) {
        log.info("Сбор {} отменён: {}", run.id(), reason);
        collectionRunRepository.save(run.cancelled(reason));
    }

    /** Сохранение, которое не бросает — для top-level catch фонового тела (не маскируем исходный сбой). */
    private void safeSave(CollectionRun run) {
        try {
            collectionRunRepository.save(run);
        } catch (Exception e) {
            log.error("Не удалось сохранить терминальный статус прогона {}", run.id(), e);
        }
    }

    private LocalDateTime resolveSince(LocalDateTime explicit) {
        if (explicit != null) return explicit;
        return collectionRunRepository.findLastSuccessfulUntil()
                .map(t -> t.plusSeconds(1))
                .orElse(DEFAULT_START_DATE);
    }
}
