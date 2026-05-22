package ru.x5.markable.dev.analytics.domain.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PeriodTest {

    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate JAN_31 = LocalDate.of(2026, 1, 31);

    @Test
    void singleDayIsValid() {
        Period p = new Period(JAN_1, JAN_1);
        assertThat(p.contains(JAN_1)).isTrue();
    }

    @Test
    void rejectsInvertedRange() {
        assertThatThrownBy(() -> new Period(JAN_31, JAN_1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before");
    }

    @Test
    void containsRespectsBothBoundsInclusive() {
        Period p = new Period(JAN_1, JAN_31);
        assertThat(p.contains(JAN_1)).isTrue();
        assertThat(p.contains(JAN_31)).isTrue();
        assertThat(p.contains(LocalDate.of(2026, 1, 15))).isTrue();
        assertThat(p.contains(LocalDate.of(2025, 12, 31))).isFalse();
        assertThat(p.contains(LocalDate.of(2026, 2, 1))).isFalse();
    }

    @Test
    void exposesTimestampsForJpaQueries() {
        Period p = new Period(JAN_1, JAN_31);
        assertThat(p.fromAtStartOfDay()).isEqualTo(JAN_1.atStartOfDay());
        assertThat(p.toAtEndOfDay().toLocalDate()).isEqualTo(JAN_31);
        assertThat(p.toAtEndOfDay().getHour()).isEqualTo(23);
    }
}
