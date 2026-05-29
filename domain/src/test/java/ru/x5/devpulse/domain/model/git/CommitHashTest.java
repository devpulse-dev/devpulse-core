package ru.x5.devpulse.domain.model.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Value Object: CommitHash")
class CommitHashTest {

    private static final String SHA1 = "a".repeat(40);
    private static final String SHA256 = "b".repeat(64);
    private static final String SHA1_UPPERCASE = "A".repeat(40);

    @Test
    @DisplayName("Принимает SHA-1 (40 hex-символов) и предоставляет короткую форму")
    void acceptsSha1AndExposesShortForm() {
        CommitHash hash = new CommitHash(SHA1);

        assertAll("SHA-1 hash",
                () -> assertThat(hash.value())
                        .as("значение сохраняется как есть")
                        .isEqualTo(SHA1),
                () -> assertThat(hash.shortValue())
                        .as("short-форма — первые 7 символов")
                        .hasSize(7)
                        .isEqualTo(SHA1.substring(0, 7)));
    }

    @Test
    @DisplayName("Принимает SHA-256 (64 hex-символа)")
    void acceptsSha256() {
        assertThat(new CommitHash(SHA256).value())
                .as("значение SHA-256 сохраняется без изменений")
                .isEqualTo(SHA256);
    }

    @Test
    @DisplayName("Нормализует к нижнему регистру")
    void lowercasesValue() {
        CommitHash hash = new CommitHash(SHA1_UPPERCASE);

        assertThat(hash.value())
                .as("hex должен быть приведён к lower-case")
                .isEqualTo(SHA1);
    }

    @ParameterizedTest(name = "[{index}] длина = {0} символов")
    @ValueSource(ints = {0, 1, 7, 39, 41, 63, 65, 100})
    @DisplayName("Отклоняет неверную длину (не 40 и не 64 символа)")
    void rejectsWrongLength(int length) {
        String invalidHash = "a".repeat(length);

        assertThatThrownBy(() -> new CommitHash(invalidHash))
                .as("hash длины %d невалиден (должна быть 40 или 64)", length)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("40");
    }

    @Test
    @DisplayName("Отклоняет нон-hex символы")
    void rejectsNonHex() {
        String invalid = "z".repeat(40);

        assertThatThrownBy(() -> new CommitHash(invalid))
                .as("\"z\" не входит в hex-алфавит")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hex");
    }
}
