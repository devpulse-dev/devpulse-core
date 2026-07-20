package ru.x5.devpulse.adapter.git;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.extern.log4j.Log4j2;
import ru.x5.devpulse.domain.common.TaskNumber;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.git.CommitHash;
import ru.x5.devpulse.domain.model.git.RepoName;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.service.CommitMessageParser;
import ru.x5.devpulse.domain.service.TestFileDetector;

/**
 * Парсер вывода {@code git log --pretty=format:%H|%ae|%P|%ad|%s --numstat}.
 *
 * <p>Pure-функция, без зависимостей от Spring/IO. Каждый коммит описан так:
 * <pre>
 * &lt;hash&gt;|&lt;email&gt;|&lt;parents&gt;|&lt;iso-date&gt;|&lt;commit-message&gt;
 * &lt;added&gt;\t&lt;deleted&gt;\t&lt;file&gt;
 * &lt;added&gt;\t&lt;deleted&gt;\t&lt;file&gt;
 * ...
 * </pre>
 *
 * <p>Numstat-строки {@code -}/{@code -} (бинарные файлы) пропускаются. Если поле
 * parents содержит пробел, коммит — merge.</p>
 *
 * <p><b>Два режима:</b>
 * <ul>
 *   <li>{@link #parse(List, RepoName)} — eager, удобен для тестов и небольших выводов;</li>
 *   <li>{@link Streaming} — push-stream state machine, кормится по одной строке через
 *       {@link Streaming#onLine(String)}, отдаёт готовые коммиты в {@link Consumer}.
 *       Память: один незавершённый коммит. Используется в {@link GitGatewayAdapter} для
 *       обхода OOM на больших репо.</li>
 * </ul></p>
 */
@Log4j2
final class GitLogParser {

    private GitLogParser() {}

    /** Eager-режим: парсит весь output разом. Tests / маленькие репо. */
    static List<Commit> parse(List<String> lines, RepoName repo) {
        if (lines == null || lines.isEmpty()) return List.of();
        List<Commit> commits = new ArrayList<>();
        Streaming s = new Streaming(repo, commits::add);
        for (String line : lines) {
            if (line != null) s.onLine(line);
        }
        s.finish();
        log.info("Распарсено {} коммитов для репозитория {}", commits.size(), repo.value());
        return commits;
    }

    /**
     * Streaming state machine: кормить через {@link #onLine}, в конце дёрнуть {@link #finish()}.
     *
     * <p>Каждый завершённый коммит сразу уходит в {@code sink}. Внутри держится максимум
     * <i>один</i> незавершённый коммит — O(1) по памяти независимо от размера output.</p>
     *
     * <p><b>Невалидная запись</b> (дата / hash / email) — коммит молча отбрасывается (warn в лог).
     * Несколько повреждённых записей не должны валить сбор. Особенно важно для email: коммиты с
     * dotless-доменом ({@code ci@runner}, {@code root@localhost}) массово встречаются в CI/легаси,
     * а {@code new Email(...)} на них бросает — без skip это исключение улетало бы на reader-VT
     * ({@code drainStdout}), убивая reader и маскируя причину под git-сбой (SIGPIPE → exit≠0).</p>
     */
    static final class Streaming {

        private final RepoName repo;
        private final Consumer<Commit> sink;

        // Состояние текущего коммита. null = "ждём header".
        private Header header;
        private long added;
        private long deleted;
        private long testAdded;

        Streaming(RepoName repo, Consumer<Commit> sink) {
            this.repo = repo;
            this.sink = sink;
        }

        void onLine(String raw) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) return; // разделитель между коммитами или начало вывода

            if (isHeader(line)) {
                flushCurrent();
                header = parseHeader(line);
                added = deleted = testAdded = 0;
            } else if (header != null && isNumstat(line)) {
                long[] delta = parseNumstat(line);
                if (delta != null) {
                    added += delta[0];
                    deleted += delta[1];
                    if (delta[2] > 0) {
                        testAdded += delta[0];
                    }
                }
            }
            // header == null и не-header строка → "мусор" в начале output, игнорируем
        }

        void finish() {
            flushCurrent();
        }

        private void flushCurrent() {
            if (header != null) {
                Commit commit = buildCommit(header, repo, added, deleted, testAdded);
                if (commit != null) {
                    sink.accept(commit);
                }
                header = null;
            }
        }
    }

    /** Заголовок — единственная строка без табуляции, содержащая @. */
    private static boolean isHeader(String line) {
        return !line.contains("\t") && line.contains("@");
    }

    private static boolean isNumstat(String line) {
        return line.contains("\t");
    }

    /** Парсит строку формата {@code hash|email|parents|date|message}. */
    private static Header parseHeader(String line) {
        int p1 = line.indexOf('|');
        int p2 = line.indexOf('|', p1 + 1);
        int p3 = line.indexOf('|', p2 + 1);
        int p4 = line.indexOf('|', p3 + 1);
        if (p1 < 0 || p2 < 0 || p3 < 0 || p4 < 0) return null;

        String hash = line.substring(0, p1).trim();
        String email = line.substring(p1 + 1, p2).trim();
        String parents = line.substring(p2 + 1, p3).trim();
        String dateStr = line.substring(p3 + 1, p4).trim();
        String message = line.substring(p4 + 1).trim();
        boolean merge = parents.contains(" ");

        try {
            ZonedDateTime zdt = ZonedDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return new Header(hash, email, zdt.toLocalDateTime(), merge, message);
        } catch (DateTimeParseException e) {
            log.warn("Не удалось распарсить дату коммита \"{}\" для {}", dateStr, email);
            return null;
        }
    }

    /** Возвращает {@code [added, deleted, isTest? 1 : 0]} или null если строку нужно пропустить. */
    private static long[] parseNumstat(String line) {
        String[] parts = line.split("\t");
        if (parts.length < 3) return null;
        if (parts[0].equals("-") || parts[1].equals("-")) return null;
        try {
            long added = Long.parseLong(parts[0]);
            long deleted = Long.parseLong(parts[1]);
            long isTest = TestFileDetector.isTestFile(parts[2]) ? 1L : 0L;
            return new long[]{added, deleted, isTest};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Собирает {@link Commit} или {@code null}, если hash/email невалидны (коммит пропускается). */
    private static Commit buildCommit(Header h, RepoName repo, long added, long deleted, long testAdded) {
        try {
            TaskNumber task = CommitMessageParser.extractTaskNumber(h.message()).orElse(null);
            return new Commit(
                    new CommitHash(h.hash()),
                    new Email(h.email()),
                    h.date(),
                    h.merge(),
                    added,
                    deleted,
                    testAdded,
                    h.message(),
                    task,
                    repo
            );
        } catch (IllegalArgumentException e) {
            // Битый hash или email (напр. dotless-домен ci@runner) — пропускаем коммит, как и
            // невалидную дату. Иначе IllegalArgumentException улетел бы на reader-VT и убил бы reader.
            log.warn("Пропущен коммит {} — невалидный hash/email (email={}): {}",
                    h.hash(), h.email(), e.getMessage());
            return null;
        }
    }

    private record Header(String hash, String email, LocalDateTime date, boolean merge, String message) {}
}
