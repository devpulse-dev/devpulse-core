package ru.x5.markable.dev.analytics.gitlab.utill;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
@Log4j2
public class CommitMessageParser {

    // Паттерн для поиска номера задачи в сообщении коммита
    // Ищет форматы: "1700-3102091"
    private static final Pattern TASK_NUMBER_PATTERN = Pattern.compile("(\\d{4}-\\d+)");

    /**
     * Извлечь номер задачи из сообщения коммита
     */
    public static String extractTaskNumber(String commitMessage) {
        if (commitMessage == null || commitMessage.isBlank()) {
            return null;
        }

        Matcher matcher = TASK_NUMBER_PATTERN.matcher(commitMessage);
        if (matcher.find()) {
            String taskNumber = matcher.group();
            log.debug("Extracted task number: {} from message: {}", taskNumber,
                    commitMessage.substring(0, Math.min(50, commitMessage.length())));
            return taskNumber;
        }

        return null;
    }
}
