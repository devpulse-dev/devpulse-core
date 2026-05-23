package ru.x5.markable.dev.analytics.adapter.git;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import ru.x5.markable.dev.analytics.domain.common.TaskNumber;
import ru.x5.markable.dev.analytics.domain.model.git.Commit;
import ru.x5.markable.dev.analytics.domain.model.git.CommitHash;
import ru.x5.markable.dev.analytics.domain.model.git.RepoName;
import ru.x5.markable.dev.analytics.domain.model.user.Email;
import ru.x5.markable.dev.analytics.domain.service.CommitMessageParser;
import ru.x5.markable.dev.analytics.domain.service.TestFileDetector;

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
 */
@Log4j2
final class GitLogParser {

    private GitLogParser() {}

    static List<Commit> parse(List<String> lines, RepoName repo) {
        if (lines == null || lines.isEmpty()) return List.of();

        List<Commit> commits = new ArrayList<>();
        Header current = null;
        long added = 0;
        long deleted = 0;
        long testAdded = 0;

        for (String raw : lines) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isEmpty()) continue;

            if (isHeader(line)) {
                if (current != null) {
                    commits.add(buildCommit(current, repo, added, deleted, testAdded));
                }
                current = parseHeader(line);
                added = deleted = testAdded = 0;
            } else if (current != null && isNumstat(line)) {
                long[] delta = parseNumstat(line);
                if (delta != null) {
                    added += delta[0];
                    deleted += delta[1];
                    if (delta[2] > 0) {
                        testAdded += delta[0];
                    }
                }
            }
        }
        if (current != null) {
            commits.add(buildCommit(current, repo, added, deleted, testAdded));
        }
        log.info("Распарсено {} коммитов для репозитория {}", commits.size(), repo.value());
        return commits;
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

    private static Commit buildCommit(Header h, RepoName repo, long added, long deleted, long testAdded) {
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
    }

    private record Header(String hash, String email, LocalDateTime date, boolean merge, String message) {}
}
