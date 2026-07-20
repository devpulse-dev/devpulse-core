package ru.x5.devpulse.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.kaiten.KaitenCard;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardId;
import ru.x5.devpulse.domain.model.kaiten.KaitenUrgency;
import ru.x5.devpulse.domain.model.performance.PeriodDefectCounts;

@DisplayName("DefectPeriodAssembler (дефекты по приоритету × периоды)")
class DefectPeriodAssemblerTest {

    private static final int TYPE_DEFECT = 8;
    private static final int TYPE_DEVELOPMENT = 70;

    private static final Period APRIL = new Period(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
    private static final Period MAY = new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

    @Test
    @DisplayName("Бакетит дефекты по createdAt и группирует по приоритету, сохраняя порядок периодов")
    void bucketsByCreatedAtAndUrgency() {
        List<KaitenCard> cards = List.of(
                defect(1, KaitenUrgency.CRITICAL, LocalDateTime.of(2026, 4, 5, 10, 0)),
                defect(2, KaitenUrgency.HIGH, LocalDateTime.of(2026, 4, 20, 10, 0)),
                defect(3, KaitenUrgency.HIGH, LocalDateTime.of(2026, 5, 2, 10, 0)),
                defect(4, KaitenUrgency.LOW, LocalDateTime.of(2026, 5, 15, 10, 0)));

        List<PeriodDefectCounts> result =
                DefectPeriodAssembler.countByPeriods(cards, List.of(APRIL, MAY));

        assertAll(
                () -> assertThat(result).hasSize(2),
                () -> assertThat(result.get(0).period()).isEqualTo(APRIL),
                () -> assertThat(result.get(0).counts().critical()).isEqualTo(1),
                () -> assertThat(result.get(0).counts().high()).isEqualTo(1),
                () -> assertThat(result.get(0).counts().total()).isEqualTo(2),
                () -> assertThat(result.get(1).period()).isEqualTo(MAY),
                () -> assertThat(result.get(1).counts().high()).isEqualTo(1),
                () -> assertThat(result.get(1).counts().low()).isEqualTo(1),
                () -> assertThat(result.get(1).counts().total()).isEqualTo(2));
    }

    @Test
    @DisplayName("Не-дефекты и карточки без createdAt игнорируются")
    void ignoresNonDefectsAndNullCreatedAt() {
        List<KaitenCard> cards = List.of(
                card(1, TYPE_DEVELOPMENT, KaitenUrgency.CRITICAL, LocalDateTime.of(2026, 4, 5, 10, 0), false),
                card(2, TYPE_DEFECT, KaitenUrgency.HIGH, null, false),
                defect(3, KaitenUrgency.MEDIUM, LocalDateTime.of(2026, 4, 10, 10, 0)));

        List<PeriodDefectCounts> result =
                DefectPeriodAssembler.countByPeriods(cards, List.of(APRIL));

        assertAll(
                () -> assertThat(result.get(0).counts().total()).isEqualTo(1),
                () -> assertThat(result.get(0).counts().medium()).isEqualTo(1),
                () -> assertThat(result.get(0).counts().critical()).isZero());
    }

    @Test
    @DisplayName("Границы периода включительны (createdAt на старте дня from и конце дня to)")
    void periodBoundsInclusive() {
        List<KaitenCard> cards = List.of(
                defect(1, KaitenUrgency.LOW, LocalDate.of(2026, 4, 1).atStartOfDay()),
                defect(2, KaitenUrgency.LOW, LocalDate.of(2026, 4, 30).atTime(23, 59, 59)),
                defect(3, KaitenUrgency.LOW, LocalDate.of(2026, 3, 31).atTime(23, 0)));

        List<PeriodDefectCounts> result =
                DefectPeriodAssembler.countByPeriods(cards, List.of(APRIL));

        assertThat(result.get(0).counts().low()).isEqualTo(2);
    }

    @Test
    @DisplayName("Пересекающиеся периоды считают дефект независимо в каждом (осознанный выбор)")
    void overlappingPeriodsCountIndependently() {
        Period wide = new Period(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 31));
        KaitenCard d = defect(1, KaitenUrgency.CRITICAL, LocalDateTime.of(2026, 4, 15, 10, 0));

        List<PeriodDefectCounts> result =
                DefectPeriodAssembler.countByPeriods(List.of(d), List.of(APRIL, wide));

        assertAll(
                () -> assertThat(result.get(0).counts().total()).isEqualTo(1),
                () -> assertThat(result.get(1).counts().total()).isEqualTo(1));
    }

    @Test
    @DisplayName("aiAgentCount считает только дефекты периода с проставленной галкой AI-Agent")
    void countsAiAgentDefects() {
        List<KaitenCard> cards = List.of(
                aiDefect(1, KaitenUrgency.CRITICAL, LocalDateTime.of(2026, 4, 5, 10, 0)),
                aiDefect(2, KaitenUrgency.LOW, LocalDateTime.of(2026, 4, 20, 10, 0)),
                defect(3, KaitenUrgency.HIGH, LocalDateTime.of(2026, 4, 10, 10, 0)),
                aiDefect(4, KaitenUrgency.LOW, LocalDateTime.of(2026, 5, 15, 10, 0)));

        List<PeriodDefectCounts> result =
                DefectPeriodAssembler.countByPeriods(cards, List.of(APRIL, MAY));

        assertAll(
                () -> assertThat(result.get(0).counts().total()).isEqualTo(3),
                () -> assertThat(result.get(0).aiAgentCount()).isEqualTo(2),
                () -> assertThat(result.get(1).counts().total()).isEqualTo(1),
                () -> assertThat(result.get(1).aiAgentCount()).isEqualTo(1));
    }

    @Test
    @DisplayName("uniqueDefectsInAnyPeriod: только дефекты в каком-либо периоде, свежие сверху, без не-дефектов")
    void uniqueDefectsInAnyPeriod() {
        List<KaitenCard> cards = List.of(
                defect(1, KaitenUrgency.HIGH, LocalDateTime.of(2026, 4, 5, 10, 0)),
                defect(2, KaitenUrgency.LOW, LocalDateTime.of(2026, 5, 20, 10, 0)),
                card(3, TYPE_DEVELOPMENT, KaitenUrgency.HIGH, LocalDateTime.of(2026, 4, 10, 10, 0), false),
                defect(4, KaitenUrgency.LOW, LocalDateTime.of(2026, 3, 1, 10, 0)));

        List<KaitenCard> result =
                DefectPeriodAssembler.uniqueDefectsInAnyPeriod(cards, List.of(APRIL, MAY));

        assertThat(result).extracting(c -> c.id().value()).containsExactly(2L, 1L); // 2 (май) свежее 1 (апр)
    }

    private static KaitenCard defect(long id, KaitenUrgency urgency, LocalDateTime createdAt) {
        return card(id, TYPE_DEFECT, urgency, createdAt, false);
    }

    private static KaitenCard aiDefect(long id, KaitenUrgency urgency, LocalDateTime createdAt) {
        return card(id, TYPE_DEFECT, urgency, createdAt, true);
    }

    private static KaitenCard card(long id, int typeId, KaitenUrgency urgency, LocalDateTime createdAt,
                                   boolean aiAgent) {
        return new KaitenCard(
                new KaitenCardId(id), "card" + id, null, typeId, 2,
                "col", "board", "space", null, null,
                createdAt, createdAt, null, false, "https://kaiten.x5.ru/" + id, List.of(),
                urgency, null, null, null, null, null, aiAgent);
    }
}
