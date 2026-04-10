package ru.x5.markable.dev.analytics.gitlab.service.impl.helper;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
import ru.x5.markable.dev.analytics.gitlab.client.GitClient;
import ru.x5.markable.dev.analytics.gitlab.model.CommitDetail;
import ru.x5.markable.dev.analytics.gitlab.service.CommitDetailsService;
import ru.x5.markable.dev.analytics.gitlab.utill.CommitMessageParser;

import java.nio.file.Path;

/**
 * Вспомогательный класс для сбора и парсинга коммитов из Git-репозиториев.
 * Отвечает за взаимодействие с Git через CLI и парсинг вывода команд.
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class GitCommitCollector {

    private final GitClient gitClient;
    private final CommitDetailsService commitDetailsService;

    /**
     * Собирает коммиты из указанного репозитория за заданный период.
     * Фильтрует коммиты по дате и сохраняет детали в базу данных.
     *
     * @param repoUrl URL репозитория
     * @param repoName имя репозитория
     * @param start начало периода
     * @param end конец периода
     * @return список деталей коммитов, попавших в указанный период
     */
    public List<CommitDetail> collectForRepositoryWithDetails(String repoUrl, String repoName,
            LocalDateTime start, LocalDateTime end) {
        List<CommitDetail> repoCommits = new ArrayList<>();

        try {
            log.debug("Processing repository [{}] for period {} - {}", repoName, start, end);

            Path repoPath = gitClient.prepareRepository(repoUrl);
            List<String> lines = gitClient.collectStats(repoPath, start, end);

            if (lines.isEmpty()) {
                log.debug("No commits found for {} in this period", repoName);
                return repoCommits;
            }

            List<CommitDetail> allCommits = parseGitOutputWithDates(lines);

            for (CommitDetail commit : allCommits) {
                LocalDateTime commitDate = commit.getCommitDate();

                if (commitDate != null && !commitDate.isBefore(start) && !commitDate.isAfter(end)) {
                    commit.setRepoName(repoName);
                    repoCommits.add(commit);
                } else {
                    log.debug("Filtered out commit from {} (period: {} - {})",
                            commitDate, start, end);
                }
            }

            if (CollectionUtils.isNotEmpty(repoCommits)) {
                commitDetailsService.saveCommitDetails(repoCommits);
                log.debug("Saved {} commit details for repo {}", repoCommits.size(), repoName);
            }

            log.debug("Repository [{}] processed, kept {} of {} commits within period",
                    repoName, repoCommits.size(), allCommits.size());

        } catch (Exception e) {
            log.error("Failed to process repository {} for period {} - {}", repoName, start, end, e);
        }

        return repoCommits;
    }

    /**
     * Парсит вывод git log с датами.
     * Формат строки с коммитом: hash|email|parent|date|message
     * Формат строки с numstat: added\tdeleted\tfilename
     *
     * @param lines список строк вывода git log
     * @return список деталей коммитов
     */
    public List<CommitDetail> parseGitOutputWithDates(List<String> lines) {
        List<CommitDetail> commits = new ArrayList<>();
        CommitDetail currentCommit = null;

        for (String rawLine : lines) {
            if (rawLine == null || rawLine.trim().isEmpty()) {
                continue;
            }

            String line = rawLine.trim();

            if (isCommitHeaderLine(line)) {
                currentCommit = parseCommitHeaderLine(line);
                if (currentCommit != null) {
                    commits.add(currentCommit);
                }
            } else if (isNumstatLine(line) && currentCommit != null) {
                processNumstatLine(line, currentCommit);
            }
        }

        log.info("Parsed {} commits from git output", commits.size());
        return commits;
    }

    /**
     * Определяет, является ли строка заголовком коммита.
     * Заголовок коммита не содержит табуляций, но содержит @ (email)
     *
     * @param line строка для проверки
     * @return true если это заголовок коммита
     */
    private boolean isCommitHeaderLine(String line) {
        return !line.contains("\t") && line.contains("@");
    }

    /**
     * Проверяет, является ли строка numstat.
     * Numstat содержит табуляцию.
     *
     * @param line строка для проверки
     * @return true если это строка numstat
     */
    private boolean isNumstatLine(String line) {
        return line.contains("\t");
    }

    /**
     * Парсит строку заголовка коммита.
     * Формат: hash|email|parent|date|message
     * Сообщение может содержать символы |, поэтому разбиваем по первым 4 разделителям
     *
     * @param line строка заголовка коммита
     * @return объект CommitDetail или null при ошибке парсинга
     */
    private CommitDetail parseCommitHeaderLine(String line) {
        // Находим позиции первых 4 разделителей
        int firstPipe = line.indexOf('|');
        if (firstPipe == -1) return null;

        int secondPipe = line.indexOf('|', firstPipe + 1);
        if (secondPipe == -1) return null;

        int thirdPipe = line.indexOf('|', secondPipe + 1);
        if (thirdPipe == -1) return null;

        int fourthPipe = line.indexOf('|', thirdPipe + 1);
        if (fourthPipe == -1) return null;

        // Извлекаем поля
        String hash = line.substring(0, firstPipe).trim();
        String email = line.substring(firstPipe + 1, secondPipe).trim();
        String parent = line.substring(secondPipe + 1, thirdPipe).trim();
        String dateStr = line.substring(thirdPipe + 1, fourthPipe).trim();
        // Всё что после 4-го разделителя — это сообщение коммита (может содержать |)
        String message = line.substring(fourthPipe + 1).trim();

        boolean isMerge = parent.contains(" ");

        try {
            LocalDateTime commitDateUTC = convertToUtc(dateStr);
            String taskNumber = CommitMessageParser.extractTaskNumber(message);

            CommitDetail commit = new CommitDetail();
            commit.setHash(hash);
            commit.setEmail(email);
            commit.setCommitDate(commitDateUTC);
            commit.setMerge(isMerge);
            commit.setAdded(0);
            commit.setDeleted(0);
            commit.setTestAdded(0);
            commit.setCommitMessage(message);
            commit.setTaskNumber(taskNumber);

            return commit;

        } catch (DateTimeParseException e) {
            log.warn("Failed to parse date: '{}' for email: {}", dateStr, email);
            return null;
        }
    }

    /**
     * Парсит строку даты в LocalDateTime, сохраняя исходное время.
     *
     * @param dateStr строка даты в формате ISO_OFFSET_DATE_TIME
     * @return LocalDateTime в исходном часовом поясе
     * @throws DateTimeParseException при ошибке парсинга
     */
    private LocalDateTime convertToUtc(String dateStr) throws DateTimeParseException {
        ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return zonedDateTime.toLocalDateTime();
    }

    /**
     * Обрабатывает строку numstat и обновляет статистику коммита.
     * Формат: added\tdeleted\tfilename
     *
     * @param line строка numstat
     * @param commit объект коммита для обновления
     */
    private void processNumstatLine(String line, CommitDetail commit) {
        String[] parts = line.split("\t");
        if (parts.length < 3) {
            return;
        }

        if (parts[0].equals("-") || parts[1].equals("-")) {
            return;
        }

        try {
            long added = Long.parseLong(parts[0]);
            long deleted = Long.parseLong(parts[1]);
            String fileName = parts[2];
            boolean isTest = isTestFile(fileName);

            commit.setAdded(commit.getAdded() + added);
            commit.setDeleted(commit.getDeleted() + deleted);
            if (isTest) {
                commit.setTestAdded(commit.getTestAdded() + added);
            }
        } catch (NumberFormatException e) {
            log.debug("Failed to parse numstat: {}", line);
        }
    }

    /**
     * Проверяет, является ли файл тестовым.
     * Тестовым считается файл, содержащий "/test/" в пути или
     * оканчивающийся на "test.java" или "tests.java".
     *
     * @param fileName имя файла
     * @return true если файл тестовый, иначе false
     */
    private boolean isTestFile(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase();
        return lower.contains("/test/") ||
                lower.endsWith("test.java") ||
                lower.endsWith("tests.java");
    }
}
