package ru.x5.markable.dev.analytics.gitlab.utill;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Утилитный класс для парсинга сообщений коммитов.
 * 
 * <p>Предоставляет методы для извлечения информации из сообщений коммитов,
 * например, номера задачи в формате "XXXX-YYYY".</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@UtilityClass
@Log4j2
public class CommitMessageParser {

    /**
     * Паттерн для извлечения номера задачи из сообщения коммита.
     * 
     * <p>Ищет номер задачи в формате "цифры-цифры", например: "1700-3102091", "12-345", "123456-789012".</p>
     */
    private static final Pattern TASK_NUMBER_PATTERN = Pattern.compile("(\\d+-\\d+)");

    /**
     * Извлекает номер задачи из сообщения коммита.
     * 
     * <p>Ищет первое совпадение с паттерном "цифры-цифры" в сообщении коммита.
     * Если номер задачи не найден или сообщение пустое, возвращает null.</p>
     * 
     * @param commitMessage сообщение коммита для анализа
     * @return номер задачи в формате "XXXX-YYYY" или null, если не найден
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
