package ru.x5.devpulse.domain.model.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.x5.devpulse.domain.model.user.Email;

@DisplayName("Value Object: HourlyBucketAuthor")
class HourlyBucketAuthorTest {

    @Test
    @DisplayName("Принимает валидный вклад автора")
    void acceptsValidAuthor() {
        HourlyBucketAuthor a = new HourlyBucketAuthor(new Email("Boris@X5.ru"), 3, 140);

        assertAll("поля вклада",
                // Email нормализует регистр сам — адаптеру не нужен лишний toLowerCase.
                () -> assertThat(a.email().value()).isEqualTo("boris@x5.ru"),
                () -> assertThat(a.commits()).isEqualTo(3),
                () -> assertThat(a.addedLines()).isEqualTo(140));
    }

    @Test
    @DisplayName("Отклоняет null email и отрицательные счётчики")
    void rejectsInvalid() {
        Email email = new Email("boris@x5.ru");

        assertAll("инварианты",
                () -> assertThatThrownBy(() -> new HourlyBucketAuthor(null, 1, 0))
                        .isInstanceOf(NullPointerException.class),
                () -> assertThatThrownBy(() -> new HourlyBucketAuthor(email, -1, 0))
                        .isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(() -> new HourlyBucketAuthor(email, 0, -1))
                        .isInstanceOf(IllegalArgumentException.class));
    }
}
