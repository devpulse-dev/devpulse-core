package ru.x5.markable.dev.analytics.domain.model.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

    @Test
    void normalizesToLowerCaseAndTrims() {
        Email e = new Email("  Boris.Osechinskiy@X5.RU  ");
        assertThat(e.value()).isEqualTo("boris.osechinskiy@x5.ru");
    }

    @Test
    void exposesLocalPart() {
        assertThat(new Email("boris@x5.ru").localPart()).isEqualTo("boris");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "no-at-sign", "@no-local.ru", "no-domain@", "no-dot@x5"})
    void rejectsMalformed(String raw) {
        assertThatThrownBy(() -> new Email(raw))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new Email(null))
                .isInstanceOf(NullPointerException.class);
    }
}
