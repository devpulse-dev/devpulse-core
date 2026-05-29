package ru.x5.devpulse.adapter.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("GitCommandFailedException — маскирование credentials")
class GitCommandFailedExceptionTest {

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "https://gitlab-ci-token:secret123@scm.x5.ru/repo.git | https://***:***@scm.x5.ru/repo.git",
            "git clone https://oauth2:abc.def-xyz@host/repo.git target | git clone https://***:***@host/repo.git target",
            "ssh://user:password@host/repo.git | ssh://***:***@host/repo.git",
            "http://x:y@a/b | http://***:***@a/b"
    })
    @DisplayName("URL с user:password → ***")
    void masksCredentialsInUrl(String input, String expected) {
        assertThat(GitCommandFailedException.maskCredentials(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "git fetch --all --prune",
            "https://scm.x5.ru/repo.git",
            "fatal: unable to access 'https://host/'",
            "просто текст без url"
    })
    @DisplayName("Строка без credentials остаётся без изменений")
    void leavesNonCredentialStringsUntouched(String input) {
        assertThat(GitCommandFailedException.maskCredentials(input)).isEqualTo(input);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("null/пустая строка не падают")
    void nullSafe(String input) {
        assertThat(GitCommandFailedException.maskCredentials(input)).isEqualTo(input);
    }

    @Test
    @DisplayName("Конструктор маскирует и в message и в tail")
    void constructorMasksBoth() {
        String command = "[git, clone, https://x:topsecret@host/r.git, /tmp/r]";
        List<String> output = List.of(
                "Cloning into '/tmp/r'...",
                "fatal: unable to access 'https://oauth2:anothersecret@host/r.git/'");

        var ex = new GitCommandFailedException(command, 128, output);

        assertAll("маскируется и в command, и в tail",
                () -> assertThat(ex.getMessage()).doesNotContain("topsecret"),
                () -> assertThat(ex.getMessage()).doesNotContain("anothersecret"),
                () -> assertThat(ex.getMessage()).contains("***:***@host"),
                // оригинальный tail — без изменений (для программной диагностики)
                () -> assertThat(ex.tail()).hasSize(2));
    }
}
