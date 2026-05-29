package ru.x5.devpulse.domain.service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ru.x5.devpulse.domain.common.TaskNumber;

/**
 * Чистая логика парсинга commit message.
 *
 * <p>Извлекает номер задачи (Kaiten card id) из сообщения. Поддерживает форматы:
 * <ul>
 *   <li><b>{@code <spaceId>-<cardId>} в начале</b> — наш основной случай Kaiten.
 *       Например {@code "1700-2963977 [ТЕХДОЛГ] ..."} → возвращаем {@code 2963977}
 *       (cardId, а не spaceId). Длина spaceId не фиксирована (3–5 цифр).</li>
 *   <li>{@code TASK-12345}, {@code KAITEN-12345}</li>
 *   <li>{@code #12345}, {@code [12345]}</li>
 *   <li>Просто число в начале сообщения: {@code "12345 fix bug"}</li>
 * </ul>
 *
 * <p>Stateless, без зависимостей. Изолируем в utility-классе с приватным конструктором.</p>
 */
public final class CommitMessageParser {

    private static final Pattern[] PATTERNS = {
            // Kaiten "spaceId-cardId" в начале сообщения — самый специфичный паттерн, идёт первым.
            // Возвращаем cardId (вторая группа цифр), не spaceId.
            Pattern.compile("^\\s*\\d+-(\\d+)\\b"),
            Pattern.compile("(?i)(?:TASK|KAITEN)[\\s\\-#:]+(\\d+)"),
            Pattern.compile("#(\\d+)"),
            Pattern.compile("\\[(\\d+)]"),
            Pattern.compile("^\\s*(\\d{3,})\\b")
    };

    private CommitMessageParser() {}

    public static Optional<TaskNumber> extractTaskNumber(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        for (Pattern p : PATTERNS) {
            Matcher m = p.matcher(message);
            if (m.find()) {
                return Optional.of(new TaskNumber(m.group(1)));
            }
        }
        return Optional.empty();
    }
}
