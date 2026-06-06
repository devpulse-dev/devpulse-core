package ru.x5.devpulse.domain.model.performance;

/**
 * Сводка по дефектам субъекта за период: всего / в работе / закрыто + разбивка по срочности.
 *
 * @param criticalHigh KPI «сколько горящих» (критичные + высокие)
 */
public record DefectsSummary(
        int total,
        int inWork,
        int closed,
        int criticalHigh,
        UrgencyCounts byUrgency
) {
    public static final DefectsSummary EMPTY =
            new DefectsSummary(0, 0, 0, 0, UrgencyCounts.EMPTY);
}
