package ru.x5.devpulse.domain.model.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Value Object: RepoName")
class RepoNameTest {

    private static final String EXPECTED_NAME = "xrg-core";

    @ParameterizedTest(name = "[{index}] из URL \"{0}\" получаем \"{1}\"")
    @CsvSource({
            "'https://scm.x5.ru/gkr/xrg-core.git', 'xrg-core'",
            "'https://scm.x5.ru/gkr/xrg-core',     'xrg-core'",
            "'xrg-core.git',                       'xrg-core'",
            "'xrg-core',                           'xrg-core'",
            "'git@github.com:org/xrg-core.git',    'xrg-core'"
    })
    @DisplayName("fromUrl: вытаскивает имя репозитория из любых форм URL")
    void parsesRepoNameFromVariousUrlForms(String url, String expected) {
        assertThat(RepoName.fromUrl(url).value())
                .as("из URL \"%s\" должно получиться \"%s\"", url, expected)
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] пустое значение: \"{0}\"")
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("Отклоняет пустое имя репозитория")
    void rejectsBlankName(String blank) {
        assertThatThrownBy(() -> new RepoName(blank))
                .as("blank-имя \"%s\" недопустимо", blank)
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("toString возвращает значение имени")
    void toStringReturnsValue() {
        assertThat(new RepoName(EXPECTED_NAME)).asString()
                .as("toString для удобства логирования")
                .isEqualTo(EXPECTED_NAME);
    }
}
