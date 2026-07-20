package ru.x5.devpulse.domain.model.performance;

import ru.x5.devpulse.domain.common.Period;

/**
 * Счётчики уникальных дефектов по приоритету за один период.
 *
 * <p>{@code counts} — разбивка по срочности (Kaiten urgency), {@link UrgencyCounts#total()}
 * даёт всего дефектов в периоде. {@code aiAgentCount} — сколько из них с проставленной галкой
 * «AI-Agent» (Kaiten {@code id_6064}). Дефект попадает в период по {@code createdAt} (см.
 * {@link ru.x5.devpulse.domain.service.DefectPeriodAssembler}).</p>
 */
public record PeriodDefectCounts(Period period, UrgencyCounts counts, int aiAgentCount) {
}
