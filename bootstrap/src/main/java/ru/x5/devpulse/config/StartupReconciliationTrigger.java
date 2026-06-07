package ru.x5.devpulse.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.x5.devpulse.application.service.StartupReconciliationService;

/**
 * Spring-триггер startup-реконсиляции. Только lifecycle-хук; вся политика — в application-сервисе
 * {@link StartupReconciliationService} (bootstrap без Lombok и без бизнес-логики — он composition root).
 */
@Component
class StartupReconciliationTrigger {

    private final StartupReconciliationService reconciliation;

    StartupReconciliationTrigger(StartupReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onApplicationReady() {
        reconciliation.reconcileOrphanedRuns();
    }
}
