package ru.x5.devpulse.domain.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.kaiten.KaitenCard;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardType;
import ru.x5.devpulse.domain.model.kaiten.KaitenColumnStatus;
import ru.x5.devpulse.domain.model.performance.MetricDelta;
import ru.x5.devpulse.domain.model.performance.PeriodMetrics;
import ru.x5.devpulse.domain.model.performance.PerformanceHighlight;
import ru.x5.devpulse.domain.model.performance.PerformanceMetrics;
import ru.x5.devpulse.domain.model.performance.TaskStatusCounts;
import ru.x5.devpulse.domain.model.performance.TaskTypeBreakdown;

/**
 * Чистая логика сборки досье к perf-review: дельты git+ревью-метрик, счёт карточек по
 * типу/статусу и формирование highlights. Stateless, без I/O — легко тестируется.
 */
public final class PerformanceReviewAssembler {

    private PerformanceReviewAssembler() {}

    /**
     * Метрики с дельтами. git+ревью считаются как {@code current vs previous} (если
     * {@code previous != null}); карточные ({@code *InWork}/{@code *Closed}) — снапшот,
     * без дельты, берутся из {@code breakdown}.
     */
    public static PerformanceMetrics metrics(PeriodMetrics current,
                                             PeriodMetrics previous,
                                             TaskTypeBreakdown breakdown) {
        return new PerformanceMetrics(
                delta(current.commits(), previous, PeriodMetrics::commits),
                delta(current.nonMergeCommits(), previous, PeriodMetrics::nonMergeCommits),
                delta(current.addedLines(), previous, PeriodMetrics::addedLines),
                delta(current.deletedLines(), previous, PeriodMetrics::deletedLines),
                delta(current.testAddedLines(), previous, PeriodMetrics::testAddedLines),
                delta(current.reviewsGiven(), previous, PeriodMetrics::reviewsGiven),
                delta(current.commentsGiven(), previous, PeriodMetrics::commentsGiven),
                delta(current.reviewsReceived(), previous, PeriodMetrics::reviewsReceived),
                delta(current.avgTimeToMergeHours(), previous, PeriodMetrics::avgTimeToMergeHours),
                delta(current.mergedMrCount(), previous, PeriodMetrics::mergedMrCount),
                MetricDelta.snapshot(breakdown.defect().inProgress()),
                MetricDelta.snapshot(breakdown.defect().done()),
                MetricDelta.snapshot(breakdown.development().inProgress()),
                MetricDelta.snapshot(breakdown.development().done()));
    }

    private static MetricDelta delta(double current, PeriodMetrics previous,
                                     ToDoubleFunction<PeriodMetrics> extractor) {
        Double prev = (previous == null) ? null : Double.valueOf(extractor.applyAsDouble(previous));
        return MetricDelta.of(current, prev);
    }

    /**
     * Разбивка карточек по типу/статусу: закрытые в периоде → {@code done}, открытые в
     * работе (columnStatus == IN_PROGRESS) → {@code inProgress}. Остальные игнорируются.
     */
    public static TaskTypeBreakdown breakdown(List<KaitenCard> cards, Period period) {
        int defectIn = 0;
        int defectDone = 0;
        int devIn = 0;
        int devDone = 0;
        for (KaitenCard card : cards) {
            KaitenCardType type = card.cardType();
            if (type != KaitenCardType.DEFECT && type != KaitenCardType.DEVELOPMENT) {
                continue;
            }
            boolean closed = closedInPeriod(card, period);
            boolean inWork = !card.isClosed() && card.columnStatus() == KaitenColumnStatus.IN_PROGRESS;
            if (!closed && !inWork) {
                continue;
            }
            if (type == KaitenCardType.DEFECT) {
                if (closed) defectDone++; else defectIn++;
            } else {
                if (closed) devDone++; else devIn++;
            }
        }
        return new TaskTypeBreakdown(
                TaskStatusCounts.of(defectIn, defectDone),
                TaskStatusCounts.of(devIn, devDone));
    }

    /**
     * Highlights-пруфы: заметные карточки со ссылкой. Сначала закрытые в периоде (сделанная
     * работа — главное доказательство), потом в работе; дефекты раньше задач. Без url — пропускаем.
     */
    public static List<PerformanceHighlight> highlights(List<KaitenCard> cards, Period period, int limit) {
        record Scored(PerformanceHighlight highlight, int rank) {}
        List<Scored> scored = new ArrayList<>();
        for (KaitenCard card : cards) {
            KaitenCardType type = card.cardType();
            if (type != KaitenCardType.DEFECT && type != KaitenCardType.DEVELOPMENT) {
                continue;
            }
            if (card.url() == null || card.url().isBlank()) {
                continue;
            }
            boolean closed = closedInPeriod(card, period);
            boolean inWork = !card.isClosed() && card.columnStatus() == KaitenColumnStatus.IN_PROGRESS;
            if (!closed && !inWork) {
                continue;
            }
            // rank: меньше = выше. Закрытые дефекты (0) → закрытые задачи (1) → дефекты в работе (2) → задачи (3).
            int rank = (closed ? 0 : 2) + (type == KaitenCardType.DEFECT ? 0 : 1);
            String subtitle = type.name() + " · " + (closed ? "DONE" : "IN_PROGRESS");
            scored.add(new Scored(
                    new PerformanceHighlight(PerformanceHighlight.Kind.CARD, card.title(), subtitle, card.url()),
                    rank));
        }
        return scored.stream()
                .sorted(Comparator.comparingInt(Scored::rank))
                .limit(Math.max(0, limit))
                .map(Scored::highlight)
                .toList();
    }

    private static boolean closedInPeriod(KaitenCard card, Period period) {
        if (!card.isClosed()) {
            return false;
        }
        // closedAt может быть пустым у DONE-карточек без таймстампа — используем updatedAt как fallback.
        LocalDateTime when = card.closedAt() != null ? card.closedAt() : card.updatedAt();
        if (when == null) {
            return false;
        }
        return !when.isBefore(period.fromAtStartOfDay()) && !when.isAfter(period.toAtEndOfDay());
    }
}
