package ru.x5.devpulse.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.x5.devpulse.application.port.out.CollectionAlreadyRunningException;
import ru.x5.devpulse.application.port.out.CollectionLock;
import ru.x5.devpulse.application.port.out.CollectionRunRepository;

/**
 * Реконсиляция осиротевших {@code RUNNING}-прогонов. Вызывается на старте приложения
 * (Spring-триггер — в bootstrap; политика — здесь, без зависимости от Spring).
 *
 * <p><b>Проблема:</b> упавший/убитый mid-run процесс оставляет {@code collection_run} в
 * {@code RUNNING} навсегда — некому дописать. С {@code GET /collection/runs/latest} это фантом,
 * который UI показывает как «сбор идёт», а cancel по нему мёртв.</p>
 *
 * <p><b>Решение (multi-instance-safe):</b> берём advisory-lock. Свободен → ни один сбор не идёт
 * нигде (single-flight) → любой {@code RUNNING} это фантом → переводим в {@code FAILED}. Занят →
 * реальный сбор идёт на другом инстансе, {@code RUNNING} не трогаем. Лок держим только на сам
 * bulk-UPDATE и сразу отпускаем (try-with-resources) — не мешаем реальному сбору стартовать следом.</p>
 */
@Slf4j
@RequiredArgsConstructor
public final class StartupReconciliationService {

    private final CollectionLock collectionLock;
    private final CollectionRunRepository collectionRunRepository;

    public void reconcileOrphanedRuns() {
        try (CollectionLock.Handle ignored = collectionLock.acquireOrThrow()) {
            int reconciled = collectionRunRepository.failOrphanedRunning();
            if (reconciled > 0) {
                log.warn("Старт: переведено {} осиротевших RUNNING-прогонов в FAILED", reconciled);
            }
        } catch (CollectionAlreadyRunningException e) {
            log.info("Старт: сбор идёт на другом инстансе — реконсиляцию RUNNING пропускаем");
        }
    }
}
