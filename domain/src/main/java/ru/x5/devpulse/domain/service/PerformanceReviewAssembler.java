package ru.x5.devpulse.domain.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.kaiten.KaitenCard;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardType;
import ru.x5.devpulse.domain.model.kaiten.KaitenColumnStatus;
import ru.x5.devpulse.domain.model.kaiten.KaitenUrgency;
import ru.x5.devpulse.domain.model.performance.CycleTime;
import ru.x5.devpulse.domain.model.performance.CycleTimeBreakdown;
import ru.x5.devpulse.domain.model.performance.DefectsSummary;
import ru.x5.devpulse.domain.model.performance.DeliveredFeature;
import ru.x5.devpulse.domain.model.performance.DevelopmentRollup;
import ru.x5.devpulse.domain.model.performance.FirefightingItem;
import ru.x5.devpulse.domain.model.performance.KaitenInsights;
import ru.x5.devpulse.domain.model.performance.MetricDelta;
import ru.x5.devpulse.domain.model.performance.NotableResults;
import ru.x5.devpulse.domain.model.performance.PeriodMetrics;
import ru.x5.devpulse.domain.model.performance.PerformanceMetrics;
import ru.x5.devpulse.domain.model.performance.RootTask;
import ru.x5.devpulse.domain.model.performance.TaskStatusCounts;
import ru.x5.devpulse.domain.model.performance.TaskTypeBreakdown;
import ru.x5.devpulse.domain.model.performance.UrgencyCounts;
import ru.x5.devpulse.domain.model.performance.UseCaseRef;
import ru.x5.devpulse.domain.model.performance.WorkBalance;

/**
 * Чистая логика сборки досье к perf-review: дельты git+ревью-метрик, счёт карточек по
 * типу/статусу и формирование «заметных результатов». Stateless, без I/O — легко тестируется.
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
            boolean defect = type == KaitenCardType.DEFECT;
            boolean build = type.isBuildWork();   // DEVELOPMENT + TASK
            if (!defect && !build) {
                continue;
            }
            boolean closed = closedInPeriod(card, period);
            if (!closed && !inWork(card)) {
                continue;
            }
            if (defect) {
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
     * Заметные результаты — два осмысленных среза:
     * <ul>
     *   <li><b>firefighting</b> — закрытые в периоде дефекты {@code urgency ∈ {CRITICAL, HIGH}},
     *       сортировка: критичные раньше → свежие раньше;</li>
     *   <li><b>deliveredFeatures</b> — корневые задачи с завершёнными юскейсами, сортировка по
     *       числу done-юскейсов (затем по размеру фичи).</li>
     * </ul>
     * По {@code limit} элементов на блок. Строится на надёжных сигналах (срочность/завершённость/
     * уровень фичи) — без member.type и time_spent (см. {@link NotableResults}).
     *
     * @param development уже посчитанный rollup (источник корневых задач) — не пересчитываем
     */
    public static NotableResults notable(List<KaitenCard> cards, Period period,
                                         DevelopmentRollup development, int limit) {
        List<FirefightingItem> firefighting = cards.stream()
                .filter(c -> c.cardType() == KaitenCardType.DEFECT)
                .filter(c -> closedInPeriod(c, period))
                .filter(c -> c.urgency() == KaitenUrgency.CRITICAL || c.urgency() == KaitenUrgency.HIGH)
                .filter(c -> c.url() != null && !c.url().isBlank())
                .sorted(Comparator
                        .comparingInt((KaitenCard c) -> c.urgency() == KaitenUrgency.CRITICAL ? 0 : 1)
                        .thenComparing(PerformanceReviewAssembler::closedAtKey, Comparator.reverseOrder()))
                .limit(Math.max(0, limit))
                .map(c -> new FirefightingItem(c.id().value(), c.title(), c.url(), c.urgency()))
                .toList();

        List<DeliveredFeature> deliveredFeatures = development.roots().stream()
                .filter(r -> r.id() != null)   // только реальные корневые задачи (не ведро «без родителя»)
                .map(PerformanceReviewAssembler::toDeliveredFeature)
                .filter(f -> f.doneCount() > 0)
                .sorted(Comparator.comparingInt(DeliveredFeature::doneCount).reversed()
                        .thenComparing(Comparator.comparingInt(DeliveredFeature::totalCount).reversed()))
                .limit(Math.max(0, limit))
                .toList();

        return new NotableResults(firefighting, deliveredFeatures);
    }

    private static DeliveredFeature toDeliveredFeature(RootTask root) {
        int done = (int) root.useCases().stream()
                .filter(u -> u.status() == KaitenColumnStatus.DONE)
                .count();
        return new DeliveredFeature(root.id(), root.title(), root.url(), done, root.useCaseCount());
    }

    /** Ключ сортировки по «свежести закрытия»: closedAt → updatedAt → MIN. */
    private static LocalDateTime closedAtKey(KaitenCard card) {
        if (card.closedAt() != null) return card.closedAt();
        return card.updatedAt() != null ? card.updatedAt() : LocalDateTime.MIN;
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

    private static boolean inWork(KaitenCard card) {
        return !card.isClosed() && card.columnStatus() == KaitenColumnStatus.IN_PROGRESS;
    }

    // ───── Развёрнутая аналитика по карточкам Kaiten (дефекты/разработка/cycle-time/баланс) ─────

    /**
     * Полная аналитика по карточкам субъекта за период. «Релевантны» карточки, закрытые в
     * периоде ИЛИ сейчас в работе (снапшот «как сейчас»). OTHER-карточки игнорируются.
     */
    public static KaitenInsights kaitenInsights(List<KaitenCard> cards, Period period) {
        List<KaitenCard> relevant = new ArrayList<>();
        for (KaitenCard c : cards) {
            if (closedInPeriod(c, period) || inWork(c)) {
                relevant.add(c);
            }
        }
        DefectsSummary defects = defects(relevant, period);
        DevelopmentRollup development = developmentRollup(relevant);
        CycleTimeBreakdown cycleTime = cycleTime(relevant, period);
        WorkBalance balance = WorkBalance.of(defects.total(), development.useCaseCount());
        return new KaitenInsights(defects, development, cycleTime, balance);
    }

    private static DefectsSummary defects(List<KaitenCard> relevant, Period period) {
        int inWork = 0;
        int closed = 0;
        int crit = 0;
        int high = 0;
        int med = 0;
        int low = 0;
        int unk = 0;
        for (KaitenCard c : relevant) {
            if (c.cardType() != KaitenCardType.DEFECT) {
                continue;
            }
            if (closedInPeriod(c, period)) closed++; else inWork++;
            switch (c.urgency()) {
                case CRITICAL -> crit++;
                case HIGH -> high++;
                case MEDIUM -> med++;
                case LOW -> low++;
                default -> unk++;
            }
        }
        return new DefectsSummary(closed + inWork, inWork, closed, crit + high,
                new UrgencyCounts(crit, high, med, low, unk));
    }

    /** Разработка (DEVELOPMENT + TASK) → группировка по корневой задаче; без родителя → одно ведро. */
    private static DevelopmentRollup developmentRollup(List<KaitenCard> relevant) {
        Map<Long, RootAcc> byParent = new LinkedHashMap<>();
        List<UseCaseRef> ungrouped = new ArrayList<>();
        int useCaseCount = 0;
        for (KaitenCard c : relevant) {
            if (!c.cardType().isBuildWork()) {
                continue;
            }
            useCaseCount++;
            UseCaseRef ref = new UseCaseRef(
                    c.id().value(), c.title(), c.url(), c.columnStatus(), c.cardType());
            if (c.hasParent()) {
                byParent.computeIfAbsent(c.parentId().value(),
                        k -> new RootAcc(c.parentTitle(), c.parentUrl())).useCases.add(ref);
            } else {
                ungrouped.add(ref);
            }
        }
        List<RootTask> roots = new ArrayList<>(byParent.size() + 1);
        byParent.forEach((id, acc) -> roots.add(new RootTask(id, acc.title, acc.url, acc.useCases)));
        roots.sort(Comparator.comparing(r -> r.title() == null ? "" : r.title()));
        if (!ungrouped.isEmpty()) {
            roots.add(RootTask.ungrouped(ungrouped));   // синтетическое ведро — последним
        }
        return new DevelopmentRollup(useCaseCount, byParent.size(), roots);
    }

    private static final class RootAcc {
        private final String title;
        private final String url;
        private final List<UseCaseRef> useCases = new ArrayList<>();

        private RootAcc(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }

    /**
     * Cycle-time (дни) по карточкам, закрытым в периоде, раздельно: дефекты vs разработка.
     * У них разная длительность — общая медиана была бы некорректной.
     */
    private static CycleTimeBreakdown cycleTime(List<KaitenCard> relevant, Period period) {
        List<Double> defectDays = new ArrayList<>();
        List<Double> devDays = new ArrayList<>();
        for (KaitenCard c : relevant) {
            if (!closedInPeriod(c, period) || c.cycleTime().isEmpty()) {
                continue;
            }
            double days = c.cycleTime().get().toMinutes() / 1440.0;
            KaitenCardType type = c.cardType();
            if (type == KaitenCardType.DEFECT) {
                defectDays.add(days);
            } else if (type.isBuildWork()) {
                devDays.add(days);
            }
        }
        return new CycleTimeBreakdown(cycleTimeOf(defectDays), cycleTimeOf(devDays));
    }

    private static CycleTime cycleTimeOf(List<Double> days) {
        if (days.isEmpty()) {
            return CycleTime.EMPTY;
        }
        days.sort(Comparator.naturalOrder());
        double mean = days.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return new CycleTime(round1(median(days)), round1(mean), days.size());
    }

    private static double median(List<Double> sortedAsc) {
        int n = sortedAsc.size();
        return (n % 2 == 1)
                ? sortedAsc.get(n / 2)
                : (sortedAsc.get(n / 2 - 1) + sortedAsc.get(n / 2)) / 2.0;
    }

    private static Double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
