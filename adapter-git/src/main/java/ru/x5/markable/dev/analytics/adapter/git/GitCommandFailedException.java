package ru.x5.markable.dev.analytics.adapter.git;

import java.util.List;

/**
 * Бросается когда git CLI завершился с ненулевым кодом возврата.
 *
 * <p>Сохраняет последние строки вывода — полезно для логов и диагностики
 * (туда же попадает stderr, потому что {@code redirectErrorStream(true)}).</p>
 */
public class GitCommandFailedException extends RuntimeException {

    private final int exitCode;
    private final transient List<String> tail;

    public GitCommandFailedException(String command, int exitCode, List<String> output) {
        super("git завершился с кодом " + exitCode + " для команды " + command
                + "; tail: " + tail(output));
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
}
