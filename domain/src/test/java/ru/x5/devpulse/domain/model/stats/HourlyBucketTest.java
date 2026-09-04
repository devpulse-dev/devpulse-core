package ru.x5.devpulse.domain.model.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import ru.x5.devpulse.domain.model.user.Email;

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

    @Test
    @DisplayName("of(): счётчики складываются из вклада авторов")
    void ofSumsAuthors() {
        HourlyBucket cell = HourlyBucket.of(2, 14, List.of(
                new HourlyBucketAuthor(new Email("a@x5.ru"), 3, 100),
                new HourlyBucketAuthor(new Email("b@x5.ru"), 4, 220)));

        assertAll("сумма по авторам",
                () -> assertThat(cell.commits()).isEqualTo(7),
                () -> assertThat(cell.addedLines()).isEqualTo(320));
    }

    @Test
    @DisplayName("of(): авторы сортируются по убыванию коммитов — вызывающий не сортирует сам")
    void ofSortsAuthorsDesc() {
        HourlyBucket cell = HourlyBucket.of(0, 10, List.of(
                new HourlyBucketAuthor(new Email("small@x5.ru"), 1, 5),
                new HourlyBucketAuthor(new Email("big@x5.ru"), 9, 500),
                new HourlyBucketAuthor(new Email("mid@x5.ru"), 4, 50)));

        assertThat(cell.authors())
                .extracting(a -> a.email().value())
                .containsExactly("big@x5.ru", "mid@x5.ru", "small@x5.ru");
    }

    @Test
    @DisplayName("Конструктор без авторов даёт пустой список, а не null")
    void authorsDefaultToEmpty() {
        assertAll("ячейка без разбивки",
                () -> assertThat(new HourlyBucket(0, 0, 1, 1).authors()).isEmpty(),
                () -> assertThat(new HourlyBucket(0, 0, 1, 1, null).authors()).isEmpty());
    }

    @Test
    @DisplayName("Список авторов копируется — мутация исходника ячейку не трогает")
    void authorsAreDefensivelyCopied() {
        List<HourlyBucketAuthor> source = new ArrayList<>();
        source.add(new HourlyBucketAuthor(new Email("a@x5.ru"), 3, 100));
        HourlyBucket cell = new HourlyBucket(0, 10, 3, 100, source);

        source.add(new HourlyBucketAuthor(new Email("b@x5.ru"), 5, 200));

        assertThat(cell.authors()).hasSize(1);
    }
}
