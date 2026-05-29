package ru.x5.devpulse.adapter.git;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Бросается когда git CLI завершился с ненулевым кодом возврата.
 *
 * <p>Сохраняет последние строки вывода — полезно для логов и диагностики
 * (туда же попадает stderr, потому что {@code redirectErrorStream(true)}).</p>
 *
 * <p><b>Безопасность:</b> message и {@link #tail} проходят через {@link #maskCredentials} —
 * если случайно где-то всплывёт {@code https://user:token@host} (например из старого
 * .git/config или из ручной отладочной правки {@link GitCliClient}), токен будет заменён
 * на {@code ***}. Defence in depth: основной канал утечки — URL — уже закрыт через
 * {@code GIT_ASKPASS}, но этот фильтр страхует от регрессий.</p>
 */
public class GitCommandFailedException extends RuntimeException {

    /** Pattern для {@code scheme://user:password@host} — маскируем user:password. */
    private static final Pattern CREDENTIALS_IN_URL =
            Pattern.compile("(://)([^/@\\s:]+):([^@\\s]+)@");

    private final int exitCode;
    private final transient List<String> tail;

    public GitCommandFailedException(String command, int exitCode, List<String> output) {
        super("git завершился с кодом " + exitCode
                + " для команды " + maskCredentials(command)
                + "; tail: " + maskCredentials(tail(output)));
        this.exitCode = exitCode;
        this.tail = output == null ? List.of() : List.copyOf(output);
    }

    public int exitCode() {
        return exitCode;
    }

    public List<String> tail() {
        return tail;
    }

    private static String tail(List<String> output) {
        if (output == null || output.isEmpty()) return "[empty]";
        int from = Math.max(0, output.size() - 5);
        return String.join(" / ", output.subList(from, output.size()));
    }

    /**
     * Заменяет {@code scheme://user:password@host} → {@code scheme://***:***@host}.
     * Visible-for-tests: оставлен package-private.
     */
    static String maskCredentials(String s) {
        if (s == null) return null;
        return CREDENTIALS_IN_URL.matcher(s).replaceAll("$1***:***@");
    }
}
