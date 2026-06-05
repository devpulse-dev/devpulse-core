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
                created, updated, closedAt, false, url, List.of());
    }
}
