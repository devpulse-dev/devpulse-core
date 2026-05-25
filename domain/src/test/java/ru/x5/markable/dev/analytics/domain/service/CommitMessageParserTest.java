package ru.x5.markable.dev.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.x5.markable.dev.analytics.domain.common.TaskNumber;

@DisplayName("Domain Service: CommitMessageParser")
class CommitMessageParserTest {

    @ParameterizedTest(name = "[{index}] \"{0}\" → задача {1}")
    @CsvSource({
            "'TASK-12345 fix bug',           '12345'",
            "'task: 7777 done',              '7777'",
            "'KAITEN-101 something',         '101'",
            "'fixed #4242 issue',            '4242'",
            "'wip [555] WIP',                '555'",
            "'12345 initial commit',         '12345'",
            // Kaiten "spaceId-cardId" в начале — возвращаем cardId (вторую группу), не spaceId.
            // spaceId может быть разной длины (3–5 цифр).
            "'1700-2963977 [ТЕХДОЛГ] тесты', '2963977'",
            "'12345-9999 fix bug',           '9999'",
            "'999-7654321 feature',          '7654321'",
            "'  1700-100 fix',               '100'"
    })
    @DisplayName("Извлекает номер задачи из поддерживаемых форматов")
    void extractsTaskNumberFromKnownFormats(String message, String expected) {
        assertThat(CommitMessageParser.extractTaskNumber(message))
                .as("из сообщения \"%s\" должен извлекаться номер %s", message, expected)
                .map(TaskNumber::value)
                .contains(expected);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" — нет номера")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "just a fix, no task", "fix bug", "42 too short"})
    @DisplayName("Возвращает Optional.empty() когда номер задачи не найден")
    void returnsEmptyWhenNoTaskNumber(String message) {
        assertThat(CommitMessageParser.extractTaskNumber(message))
                .as("в сообщении \"%s\" нет номера задачи", message)
                .isEmpty();
    }

    @Test
    @DisplayName("Несколько форматов в одном сообщении: возвращает первый сматчившийся")
    void picksFirstMatchingPattern() {
        // Порядок паттернов: spaceId-cardId, TASK-/KAITEN-, #, [], standalone.
        // Здесь spaceId-cardId не подходит (нет ведущих цифр) — выигрывает TASK-N.
        var result = CommitMessageParser.extractTaskNumber("TASK-100 closes #200 and [300]");

        assertAll("результат парсинга при множественных вхождениях",
                () -> assertThat(result).isPresent(),
                () -> assertThat(result.map(TaskNumber::value))
                        .as("приоритет — TASK-N паттерн")
                        .contains("100"));
    }

    @Test
    @DisplayName("spaceId-cardId побеждает standalone-число — НЕ берём spaceId как номер задачи")
    void kaitenPatternBeatsStandalone() {
        var result = CommitMessageParser.extractTaskNumber("1700-2963977 [ТЕХДОЛГ] тесты");

        assertAll("приоритет Kaiten spaceId-cardId",
                () -> assertThat(result).isPresent(),
                () -> assertThat(result.map(TaskNumber::value))
                        .as("возвращаем cardId (2963977), а НЕ spaceId (1700)")
                        .contains("2963977"));
    }
}
