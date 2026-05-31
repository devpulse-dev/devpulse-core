package ru.x5.devpulse.domain.model.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Value Object: HourlyBucket")
class HourlyBucketTest {

    @Test
    @DisplayName("Принимает валидную ячейку (граничные weekday=6, hour=23)")
    void acceptsValidCell() {
        HourlyBucket cell = new HourlyBucket(6, 23, 7, 320);

        assertAll("поля ячейки",
                () -> assertThat(cell.weekday()).isEqualTo(6),
                () -> assertThat(cell.hour()).isEqualTo(23),
                () -> assertThat(cell.commits()).isEqualTo(7),
                () -> assertThat(cell.addedLines()).isEqualTo(320));
    }

    @ParameterizedTest(name = "[{index}] weekday={0} вне 0..6")
    @ValueSource(ints = {-1, 7, 100})
    @DisplayName("Отклоняет weekday вне диапазона 0..6")
    void rejectsWeekdayOutOfRange(int weekday) {
        assertThatThrownBy(() -> new HourlyBucket(weekday, 10, 1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weekday");
    }

    @ParameterizedTest(name = "[{index}] hour={0} вне 0..23")
    @ValueSource(ints = {-1, 24, 99})
    @DisplayName("Отклоняет hour вне диапазона 0..23")
    void rejectsHourOutOfRange(int hour) {
        assertThatThrownBy(() -> new HourlyBucket(0, hour, 1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hour");
    }

    @Test
    @DisplayName("Отклоняет отрицательные счётчики")
    void rejectsNegativeCounters() {
        assertAll("негативные счётчики",
                () -> assertThatThrownBy(() -> new HourlyBucket(0, 0, -1, 0))
                        .isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(() -> new HourlyBucket(0, 0, 0, -1))
                        .isInstanceOf(IllegalArgumentException.class));
    }
}
