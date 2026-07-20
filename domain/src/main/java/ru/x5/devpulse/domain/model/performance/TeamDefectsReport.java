package ru.x5.devpulse.domain.model.performance;

import java.util.List;

/**
 * Отчёт по дефектам команды: разбивка по приоритету за несколько периодов + детализация.
 *
 * <p>Обслуживает {@code POST /api/v2/stats/defects}. Дефекты уникальны (дедуп по id карточки —
 * в карточке может быть несколько участников команды, считаем один раз). Порядок {@code periods}
 * соответствует порядку периодов в запросе. {@code defects} — плоский список уникальных дефектов,
 * попавших хотя бы в один период (для детальной таблицы).</p>
 */
public record TeamDefectsReport(
        String team,
        List<PeriodDefectCounts> periods,
        List<DefectDetail> defects) {

    public TeamDefectsReport {
        periods = periods == null ? List.of() : List.copyOf(periods);
        defects = defects == null ? List.of() : List.copyOf(defects);
    }
}
