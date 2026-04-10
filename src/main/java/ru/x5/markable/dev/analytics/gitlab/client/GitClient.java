package ru.x5.markable.dev.analytics.gitlab.client;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.x5.markable.dev.analytics.gitlab.config.GitProperties;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Клиент для работы с Git через командную строку.
 * 
 * <p>Предоставляет методы для клонирования репозиториев, получения обновлений
 * и сбора статистики коммитов с помощью Git CLI.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class GitClient {

    /**
     * Конфигурация Git.
     */
    private final GitProperties gitProperties;

    /**
     * Подготавливает репозиторий для анализа: клонирует или обновляет его.
     * 
     * @param repoUrl URL репозитория
     * @return путь к локальной копии репозитория
     * @throws IOException при ошибке ввода-вывода
     * @throws InterruptedException при прерывании процесса
     */
    public Path prepareRepository(String repoUrl)
            throws IOException, InterruptedException {

        String repoName = extractRepoName(repoUrl);

        Path repoPath = Paths.get(
                gitProperties.getCacheDirectory(),
                repoName
        );

        String authenticatedUrl = buildAuthenticatedUrl(repoUrl);

        if (Files.notExists(repoPath)) {

            log.info("Cloning repository [{}]", repoName);

            execute(null,
                    "git", "clone",
                    authenticatedUrl,
                    repoPath.toString());

        } else {

            log.info("Fetching updates for [{}]", repoName);

            execute(repoPath,
                    "git", "fetch", "--all", "--prune");
        }

        return repoPath;
    }

    /**
     * Собирает статистику коммитов из репозитория за указанный период.
     * 
     * @param repoPath путь к репозиторию
     * @param since начало периода (может быть null)
     * @param until конец периода (может быть null)
     * @return список строк с результатами git log
     * @throws IOException при ошибке ввода-вывода
     * @throws InterruptedException при прерывании процесса
     */
    public List<String> collectStats(Path repoPath,
                                     LocalDate since,
                                     LocalDate until)
            throws IOException, InterruptedException {

        List<String> command = new ArrayList<>();

        command.add("git");
        command.add("log");
        command.add("--all");

        if (since != null) {
            command.add("--since=" + since);
        }

        if (until != null) {
            command.add("--until=" + until);
        }

        command.add("--pretty=format:%ae|%P");
        command.add("--numstat");

        log.info("Executing git log in [{}] period: {} - {}",
                repoPath.getFileName(),
                since,
                until);

        long start = System.currentTimeMillis();

        List<String> result =
                execute(repoPath, command.toArray(new String[0]));

        long duration = System.currentTimeMillis() - start;

        log.info("git log finished for [{}] in {} ms",
                repoPath.getFileName(),
                duration);

        return result;
    }

    /**
     * Собирает детальную статистику коммитов из репозитория за указанный период.
     * 
     * @param repoPath путь к репозиторию
     * @param since начало периода (может быть null)
     * @param until конец периода (может быть null)
     * @return список строк с результатами git log
     * @throws IOException при ошибке ввода-вывода
     * @throws InterruptedException при прерывании процесса
     */
    public List<String> collectStats(
            Path repoPath,
            LocalDateTime since,
            LocalDateTime until
    ) throws IOException, InterruptedException {

        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("log");
        command.add("--all");
        command.add("--pretty=format:%H|%ae|%P|%ad|%s");
        command.add("--date=iso-strict");
        command.add("--numstat");

        if (since != null) {
            command.add("--since=\"" + since + "\"");
        }
        if (until != null) {
            command.add("--until=\"" + until + "\"");
        }

        return execute(repoPath, command.toArray(new String[0]));
    }

    /**
     * Выполняет команду Git в указанной директории.
     * 
     * @param workingDir рабочая директория (может быть null)
     * @param command команда и её аргументы
     * @return список строк вывода команды
     * @throws IOException при ошибке ввода-вывода
     * @throws InterruptedException при прерывании процесса
     */
    private List<String> execute(Path workingDir, String... command)
            throws IOException, InterruptedException {

        ProcessBuilder pb = new ProcessBuilder(command);

        if (workingDir != null) {
            pb.directory(workingDir.toFile());
        }

        pb.redirectErrorStream(true);

        Process process = pb.start();

        List<String> lines = new ArrayList<>();

        try (BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader(process.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException(
                    "Git command failed: " + Arrays.toString(command));
        }

        return lines;
    }

    /**
     * Строит URL с токеном аутентификации для доступа к репозиторию.
     * 
     * @param repoUrl исходный URL репозитория
     * @return URL с токеном аутентификации или исходный URL, если токен не задан
     */
    private String buildAuthenticatedUrl(String repoUrl) {

        String token = gitProperties.getToken();

        if (token == null || token.isBlank()) {
            return repoUrl;
        }

        if (repoUrl.startsWith("https://")) {
            return repoUrl.replace(
                    "https://",
                    "https://gitlab-ci-token:" + token + "@"
            );
        }

        return repoUrl;
    }

    /**
     * Извлекает имя репозитория из URL.
     * 
     * @param repoUrl URL репозитория
     * @return имя репозитория без расширения .git
     */
    private String extractRepoName(String repoUrl) {
        return repoUrl.substring(repoUrl.lastIndexOf("/") + 1)
                .replace(".git", "");
    }
}