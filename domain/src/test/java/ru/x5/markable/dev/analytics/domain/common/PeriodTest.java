package ru.x5.markable.dev.analytics.domain.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Value Object: Period")
class PeriodTest {

    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate JAN_15 = LocalDate.of(2026, 1, 15);
    private static final LocalDate JAN_31 = LocalDate.of(2026, 1, 31);
    private static final LocalDate DEC_31_2025 = LocalDate.of(2025, 12, 31);
    private static final LocalDate FEB_1 = LocalDate.of(2026, 2, 1);

    @Test
    @DisplayName("Принимает интервал из одного дня (from == to)")
    void acceptsSingleDayRange() {
        Period period = new Period(JAN_1, JAN_1);

        assertThat(period.contains(JAN_1))
                .as("один день — валидный период, и он содержит сам себя")
                .isTrue();
    }

    @Test
    @DisplayName("Отклоняет инвертированный диапазон (to раньше from)")
    void rejectsInvertedRange() {
        assertThatThrownBy(() -> new Period(JAN_31, JAN_1))
                .as("to=%s раньше from=%s — должна быть ошибка", JAN_1, JAN_31)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before");
    }

    @Test
    @DisplayName("contains: обе границы включаются, даты вне диапазона исключаются")
    void containsRespectsBothBoundsInclusive() {
        Period period = new Period(JAN_1, JAN_31);

        assertAll("проверка вхождения дат в период [%s..%s]".formatted(JAN_1, JAN_31),
                () -> assertThat(period.contains(JAN_1))
                        .as("левая граница включена")
                        .isTrue(),
                () -> assertThat(period.contains(JAN_31))
                        .as("правая граница включена")
                        .isTrue(),
                () -> assertThat(period.contains(JAN_15))
                        .as("дата внутри периода входит")
                        .isTrue(),
                () -> assertThat(period.contains(DEC_31_2025))
                        .as("дата до начала не входит")
                        .isFalse(),
                () -> assertThat(period.contains(FEB_1))
                        .as("дата после конца не входит")
                        .isFalse());
    }

    @Test
    @DisplayName("fromAtStartOfDay / toAtEndOfDay дают корректные TIMESTAMP-границы")
    void exposesTimestampsForJpaQueries() {
        Period period = new Period(JAN_1, JAN_31);

        assertAll("границы для TIMESTAMP-фильтров",
                () -> assertThat(period.fromAtStartOfDay())
                        .as("начало периода — полночь даты from")
                        .isEqualTo(JAN_1.atStartOfDay()),
                () -> assertThat(period.toAtEndOfDay().toLocalDate())
                        .as("конец периода имеет дату to")
                        .isEqualTo(JAN_31),
                () -> assertThat(period.toAtEndOfDay().getHour())
                        .as("конец периода — последний час дня (23)")
                        .isEqualTo(23));
    }
}
