package ru.x5.devpulse.domain.model.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Value Object: Email")
class EmailTest {

    private static final String RAW_EMAIL = "  Boris.Osechinskiy@X5.RU  ";
    private static final String NORMALIZED_EMAIL = "boris.osechinskiy@x5.ru";
    private static final String LOCAL_PART = "boris.osechinskiy";

    @Test
    @DisplayName("Нормализует к нижнему регистру, обрезает пробелы и предоставляет local-part")
    void normalizesValueAndExposesLocalPart() {
        Email email = new Email(RAW_EMAIL);

        assertAll("нормализация email",
                () -> assertThat(email.value())
                        .as("значение приведено к lower-case и обрезано от пробелов")
                        .isEqualTo(NORMALIZED_EMAIL),
                () -> assertThat(email.localPart())
                        .as("local-part — часть до символа @")
                        .isEqualTo(LOCAL_PART),
                () -> assertThat(email).asString()
                        .as("toString() возвращает нормализованное значение")
                        .isEqualTo(NORMALIZED_EMAIL));
    }

    @ParameterizedTest(name = "[{index}] невалидный email: \"{0}\"")
    @ValueSource(strings = {"", "   ", "no-at-sign", "@no-local.ru", "no-domain@", "no-dot@x5"})
    @DisplayName("Отклоняет некорректные адреса")
    void rejectsMalformedAddresses(String raw) {
        assertThatThrownBy(() -> new Email(raw))
                .as("конструктор должен бросить IllegalArgumentException на \"%s\"", raw)
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Отклоняет null с NullPointerException")
    void rejectsNull() {
        assertThatThrownBy(() -> new Email(null))
                .as("null-значение недопустимо — ожидается NPE")
                .isInstanceOf(NullPointerException.class);
    }
}
