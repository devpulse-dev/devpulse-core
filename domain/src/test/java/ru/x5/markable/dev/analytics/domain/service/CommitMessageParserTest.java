package ru.x5.markable.dev.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.x5.markable.dev.analytics.domain.common.TaskNumber;

class CommitMessageParserTest {

    @ParameterizedTest
    @CsvSource({
            "'TASK-12345 fix bug',                       12345",
            "'task: 7777 done',                          7777",
            "'KAITEN-101 something',                     101",
            "'fixed #4242 issue',                        4242",
            "'wip [555] WIP',                            555",
            "'12345 initial commit',                     12345"
    })
    void extractsKnownFormats(String message, long expected) {
        assertThat(CommitMessageParser.extractTaskNumber(message))
                .map(TaskNumber::value)
                .contains(String.valueOf(expected));
    }

    @Test
    void returnsEmptyForMessagesWithoutTaskNumber() {
        assertThat(CommitMessageParser.extractTaskNumber("just a fix, no task"))
                .isEmpty();
    }

    @Test
    void returnsEmptyForBlank() {
        assertThat(CommitMessageParser.extractTaskNumber("")).isEmpty();
        assertThat(CommitMessageParser.extractTaskNumber("   ")).isEmpty();
        assertThat(CommitMessageParser.extractTaskNumber(null)).isEmpty();
    }

    @Test
    void ignoresShortNumbers() {
        // standalone-режим срабатывает только на 3+ цифры
        assertThat(CommitMessageParser.extractTaskNumber("42 fix")).isEmpty();
    }
}
