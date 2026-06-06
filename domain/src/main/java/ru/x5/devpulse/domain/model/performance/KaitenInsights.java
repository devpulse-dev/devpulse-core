package ru.x5.devpulse.domain.model.performance;

/**
 * Развёрнутая аналитика по карточкам Kaiten для perf-review: дефекты по срочности,
 * разработка по корневым задачам, cycle-time и баланс работы. Снапшот «как сейчас».
 */
public record KaitenInsights(
        DefectsSummary defects,
        DevelopmentRollup development,
        CycleTimeBreakdown cycleTime,
        WorkBalance balance
) {
    public static final KaitenInsights EMPTY = new KaitenInsights(
            DefectsSummary.EMPTY, DevelopmentRollup.EMPTY, CycleTimeBreakdown.EMPTY, WorkBalance.EMPTY);
}
