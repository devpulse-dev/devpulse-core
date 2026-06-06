package ru.x5.devpulse.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.kaiten.KaitenCard;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardId;
import ru.x5.devpulse.domain.model.kaiten.KaitenUrgency;
import ru.x5.devpulse.domain.model.performance.KaitenInsights;
import ru.x5.devpulse.domain.model.performance.MetricDelta;
import ru.x5.devpulse.domain.model.performance.PeriodMetrics;
import ru.x5.devpulse.domain.model.performance.PerformanceHighlight;
import ru.x5.devpulse.domain.model.performance.PerformanceMetrics;
import ru.x5.devpulse.domain.model.performance.TaskStatusCounts;
import ru.x5.devpulse.domain.model.performance.TaskTypeBreakdown;

@DisplayName("PerformanceReviewAssembler (сборка досье к perf-review)")
class PerformanceReviewAssemblerTest {

    private static final int TYPE_DEVELOPMENT = 70;
    private static final int TYPE_DEFECT = 8;
    private static final int COL_IN_PROGRESS = 2;
    private static final int COL_DONE = 3;

    private static final Period Q1 = new Period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

    @Nested
    @DisplayName("metrics: дельты git/ревью + снапшот карточек")
    class Metrics {

        private final PeriodMetrics current =
                new PeriodMetrics(100, 90, 1000, 500, 200, 20, 40, 10, 18.5, 8);
        private final TaskTypeBreakdown breakdown =
                new TaskTypeBreakdown(TaskStatusCounts.of(2, 5), TaskStatusCounts.of(3, 7));

        @Test
        @DisplayName("С предыдущим периодом — считает delta и deltaPct для историчных метрик")
        void computesDeltasWhenPreviousPresent() {
            PeriodMetrics previous =
                    new PeriodMetrics(80, 72, 800, 400, 150, 10, 30, 8, 22.0, 5);

            PerformanceMetrics m = PerformanceReviewAssembler.metrics(current, previous, breakdown);

            assertAll("дельты",
                    () -> assertThat(m.commits().current()).isEqualTo(100.0),
                    () -> assertThat(m.commits().previous()).isEqualTo(80.0),
                    () -> assertThat(m.commits().delta()).isEqualTo(20.0),
                    () -> assertThat(m.commits().deltaPct()).isEqualTo(25.0),
                    // карточные — всегда снапшот, без дельты, даже когда previous есть
                    () -> assertThat(m.defectsInWork()).isEqualTo(MetricDelta.snapshot(2)),
                    () -> assertThat(m.defectsClosed()).isEqualTo(MetricDelta.snapshot(5)),
                    () -> assertThat(m.devTasksInWork()).isEqualTo(MetricDelta.snapshot(3)),
                    () -> assertThat(m.devTasksClosed()).isEqualTo(MetricDelta.snapshot(7)));
        }

        @Test
        @DisplayName("Без предыдущего периода — историчные метрики тоже снапшот (previous/delta = null)")
        void snapshotWhenPreviousNull() {
            PerformanceMetrics m = PerformanceReviewAssembler.metrics(current, null, breakdown);

            assertAll("без сравнения",
                    () -> assertThat(m.commits().current()).isEqualTo(100.0),
                    () -> assertThat(m.commits().previous()).isNull(),
                    () -> assertThat(m.commits().delta()).isNull(),
                    () -> assertThat(m.commits().deltaPct()).isNull());
        }

        @Test
        @DisplayName("deltaPct = null при previous == 0 (деления на ноль нет)")
        void deltaPctNullOnZeroPrevious() {
            MetricDelta d = MetricDelta.of(10, 0.0);
            assertAll("ноль в знаменателе",
                    () -> assertThat(d.delta()).isEqualTo(10.0),
                    () -> assertThat(d.deltaPct()).isNull());
        }
    }

    @Nested
    @DisplayName("breakdown: счёт карточек по типу/статусу")
    class Breakdown {

        @Test
        @DisplayName("Закрытые в периоде → done, в работе → inProgress; OTHER и закрытые вне периода игнорируются")
        void countsByTypeAndStatus() {
            LocalDateTime inQ1 = LocalDateTime.of(2026, 2, 10, 12, 0);
            LocalDateTime beforeQ1 = LocalDateTime.of(2025, 12, 10, 12, 0);
            List<KaitenCard> cards = List.of(
                    card(1, TYPE_DEFECT, COL_DONE, inQ1, "fixed defect"),          // defect done
                    card(2, TYPE_DEFECT, COL_IN_PROGRESS, null, "defect wip"),     // defect inProgress
                    card(3, TYPE_DEVELOPMENT, COL_DONE, inQ1, "shipped feature"),  // dev done
                    card(4, TYPE_DEVELOPMENT, COL_IN_PROGRESS, null, "feature wip"), // dev inProgress
                    card(5, TYPE_DEFECT, COL_DONE, beforeQ1, "old defect"),        // закрыт ВНЕ периода → игнор
                    card(6, 999, COL_IN_PROGRESS, null, "other type"));            // OTHER → игнор

            TaskTypeBreakdown bd = PerformanceReviewAssembler.breakdown(cards, Q1);

            assertAll("разбивка",
                    () -> assertThat(bd.defect()).isEqualTo(TaskStatusCounts.of(1, 1)),       // inProgress 1, done 1
                    () -> assertThat(bd.development()).isEqualTo(TaskStatusCounts.of(1, 1)),
                    () -> assertThat(bd.defect().total()).isEqualTo(2));
        }
    }

    @Nested
    @DisplayName("highlights: пруфы со ссылками")
    class Highlights {

        @Test
        @DisplayName("Закрытые дефекты раньше остального; карточки без url пропускаются; limit соблюдается")
        void ordersAndLimits() {
            LocalDateTime inQ1 = LocalDateTime.of(2026, 2, 10, 12, 0);
            List<KaitenCard> cards = List.of(
                    card(1, TYPE_DEVELOPMENT, COL_IN_PROGRESS, null, "dev wip"),
                    card(2, TYPE_DEFECT, COL_DONE, inQ1, "closed defect"),
                    cardNoUrl(3, TYPE_DEFECT, COL_DONE, inQ1, "defect no url"));

            List<PerformanceHighlight> hl = PerformanceReviewAssembler.highlights(cards, Q1, 10);

            assertAll("highlights",
                    () -> assertThat(hl).hasSize(2),                              // без-url отброшен
                    () -> assertThat(hl.get(0).title()).isEqualTo("closed defect"), // закрытый дефект — первый
                    () -> assertThat(hl.get(0).kind()).isEqualTo(PerformanceHighlight.Kind.CARD),
                    () -> assertThat(hl.get(0).subtitle()).isEqualTo("DEFECT · DONE"));
        }

        @Test
        @DisplayName("limit=0 → пустой список")
        void respectsZeroLimit() {
            LocalDateTime inQ1 = LocalDateTime.of(2026, 2, 10, 12, 0);
            List<PerformanceHighlight> hl = PerformanceReviewAssembler.highlights(
                    List.of(card(1, TYPE_DEFECT, COL_DONE, inQ1, "x")), Q1, 0);
            assertThat(hl).isEmpty();
        }
    }

    private static KaitenCard card(long id, int typeId, int columnType,
                                   LocalDateTime closedAt, String title) {
        return buildCard(id, typeId, columnType, closedAt, title, "https://kaiten.x5.ru/" + id);
    }

    private static KaitenCard cardNoUrl(long id, int typeId, int columnType,
                                        LocalDateTime closedAt, String title) {
        return buildCard(id, typeId, columnType, closedAt, title, null);
    }

    private static KaitenCard buildCard(long id, int typeId, int columnType,
                                        LocalDateTime closedAt, String title, String url) {
        LocalDateTime created = LocalDateTime.of(2026, 1, 5, 10, 0);
        LocalDateTime updated = closedAt != null ? closedAt : LocalDateTime.of(2026, 2, 1, 10, 0);
        return new KaitenCard(
                new KaitenCardId(id), title, null, typeId, columnType,
                "col", "board", "space", null, null,
                created, updated, closedAt, false, url, List.of(),
                null, null, null, null, null, null);
    }

    @Nested
    @DisplayName("kaitenInsights: дефекты по срочности / rollup разработки / cycle-time / баланс")
    class Insights {

        @Test
        @DisplayName("Дефекты: total/inWork/closed + разбивка по срочности + criticalHigh")
        void defectsByUrgency() {
            LocalDateTime done = LocalDateTime.of(2026, 2, 10, 12, 0);
            List<KaitenCard> cards = List.of(
                    defect(1, COL_DONE, done, KaitenUrgency.CRITICAL),
                    defect(2, COL_DONE, done, KaitenUrgency.HIGH),
                    defect(3, COL_IN_PROGRESS, null, KaitenUrgency.MEDIUM),
                    defect(4, COL_DONE, done, KaitenUrgency.LOW),
                    defect(5, COL_DONE, done, KaitenUrgency.UNKNOWN));

            var d = PerformanceReviewAssembler.kaitenInsights(cards, Q1).defects();

            assertAll("дефекты",
                    () -> assertThat(d.total()).isEqualTo(5),
                    () -> assertThat(d.closed()).isEqualTo(4),
                    () -> assertThat(d.inWork()).isEqualTo(1),
                    () -> assertThat(d.criticalHigh()).isEqualTo(2),
                    () -> assertThat(d.byUrgency().critical()).isEqualTo(1),
                    () -> assertThat(d.byUrgency().high()).isEqualTo(1),
                    () -> assertThat(d.byUrgency().medium()).isEqualTo(1),
                    () -> assertThat(d.byUrgency().low()).isEqualTo(1),
                    () -> assertThat(d.byUrgency().unknown()).isEqualTo(1));
        }

        @Test
        @DisplayName("Разработка: rollup по родителю; задачи (тип 6) тоже build-work; без родителя — ведро")
        void developmentRollup() {
            LocalDateTime done = LocalDateTime.of(2026, 2, 10, 12, 0);
            List<KaitenCard> cards = List.of(
                    build(10, TYPE_DEVELOPMENT, COL_DONE, done, 100L, "Root A"),
                    build(11, TYPE_DEVELOPMENT, COL_IN_PROGRESS, null, 100L, "Root A"),
                    build(12, TYPE_TASK, COL_DONE, done, 200L, "Root B"),
                    build(13, TYPE_DEVELOPMENT, COL_DONE, done, null, null));

            var dev = PerformanceReviewAssembler.kaitenInsights(cards, Q1).development();

            assertAll("разработка",
                    () -> assertThat(dev.useCaseCount()).isEqualTo(4),
                    () -> assertThat(dev.rootTaskCount()).as("реальных родителей: 100, 200").isEqualTo(2),
                    () -> assertThat(dev.roots()).hasSize(3),
                    () -> assertThat(dev.roots().get(0).title()).isEqualTo("Root A"),
                    () -> assertThat(dev.roots().get(0).useCaseCount()).isEqualTo(2),
                    () -> assertThat(dev.roots().get(dev.roots().size() - 1).isUngrouped())
                            .as("ведро без родителя — последним").isTrue());
        }

        @Test
        @DisplayName("Cycle-time: медиана/среднее в днях по закрытым в периоде")
        void cycleTime() {
            List<KaitenCard> cards = List.of(
                    withCycle(20, LocalDateTime.of(2026, 2, 1, 9, 0), LocalDateTime.of(2026, 2, 4, 9, 0)),
                    withCycle(21, LocalDateTime.of(2026, 2, 1, 9, 0), LocalDateTime.of(2026, 2, 6, 9, 0)));

            var ct = PerformanceReviewAssembler.kaitenInsights(cards, Q1).cycleTime();

            assertAll("cycle-time",
                    () -> assertThat(ct.count()).isEqualTo(2),
                    () -> assertThat(ct.medianDays()).isEqualTo(4.0),
                    () -> assertThat(ct.meanDays()).isEqualTo(4.0));
        }

        @Test
        @DisplayName("Баланс: доли дефектов vs build-работы (3:1 → 0.75/0.25)")
        void balance() {
            LocalDateTime done = LocalDateTime.of(2026, 2, 10, 12, 0);
            List<KaitenCard> cards = List.of(
                    defect(1, COL_DONE, done, KaitenUrgency.HIGH),
                    defect(2, COL_DONE, done, KaitenUrgency.LOW),
                    defect(3, COL_DONE, done, KaitenUrgency.MEDIUM),
                    build(10, TYPE_DEVELOPMENT, COL_DONE, done, 100L, "Root A"));

            var b = PerformanceReviewAssembler.kaitenInsights(cards, Q1).balance();

            assertAll("баланс",
                    () -> assertThat(b.defectCount()).isEqualTo(3),
                    () -> assertThat(b.buildCount()).isEqualTo(1),
                    () -> assertThat(b.defectShare()).isEqualTo(0.75),
                    () -> assertThat(b.buildShare()).isEqualTo(0.25));
        }
    }

    private static final int TYPE_TASK = 6;

    private static KaitenCard defect(long id, int columnType, LocalDateTime closedAt, KaitenUrgency urgency) {
        return insightCard(id, TYPE_DEFECT, columnType, closedAt, urgency, null, null, null, null);
    }

    private static KaitenCard build(long id, int typeId, int columnType, LocalDateTime closedAt,
                                    Long parentId, String parentTitle) {
        return insightCard(id, typeId, columnType, closedAt, KaitenUrgency.UNKNOWN, parentId, parentTitle, null, null);
    }

    private static KaitenCard withCycle(long id, LocalDateTime inProgress, LocalDateTime done) {
        return insightCard(id, TYPE_DEVELOPMENT, COL_DONE, done, KaitenUrgency.UNKNOWN, null, null, inProgress, done);
    }

    private static KaitenCard insightCard(long id, int typeId, int columnType, LocalDateTime closedAt,
                                          KaitenUrgency urgency, Long parentId, String parentTitle,
                                          LocalDateTime inProgressAt, LocalDateTime doneAt) {
        LocalDateTime created = LocalDateTime.of(2026, 1, 5, 10, 0);
        LocalDateTime updated = closedAt != null ? closedAt : LocalDateTime.of(2026, 2, 1, 10, 0);
        KaitenCardId parent = parentId == null ? null : new KaitenCardId(parentId);
        String parentUrl = parentId == null ? null : "https://k/" + parentId;
        return new KaitenCard(
                new KaitenCardId(id), "card" + id, null, typeId, columnType,
                "col", "board", "space", null, null,
                created, updated, closedAt, false, "https://k/" + id, List.of(),
                urgency, parent, parentTitle, parentUrl, inProgressAt, doneAt);
    }
}
