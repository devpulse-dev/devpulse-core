package ru.x5.devpulse.adapter.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.x5.devpulse.domain.model.git.RepoName;

/**
 * Тонкая обёртка над git CLI: clone / fetch / log с numstat-форматом.
 *
 * <p>Stateless, потокобезопасен — все методы возвращают результат, не держат состояние.
 * Конкуренция между потоками решается тем, что на каждый репозиторий — своя поддиректория.</p>
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class GitCliClient {

    /** Формат строки заголовка коммита: hash|email|parents|date|message */
    static final String LOG_FORMAT = "%H|%ae|%P|%ad|%s";

    private final GitProperties properties;

    /**
     * Клонирует репозиторий в кеш либо делает {@code git fetch --all --prune}, если он уже есть.
     *
     * @param repoUrl URL репозитория
     * @return локальный путь к репозиторию + извлечённое имя
     */
    public PreparedRepo prepare(String repoUrl) throws IOException, InterruptedException {
        RepoName name = RepoName.fromUrl(repoUrl);
        Path repoPath = properties.cacheDirectory().resolve(name.value());
        String url = withToken(repoUrl);

        if (Files.notExists(repoPath)) {
            log.info("Клонирую репозиторий [{}]", name);
            execute(null, "git", "clone", url, repoPath.toString());
        } else {
            log.info("Обновляю репозиторий [{}]", name);
            execute(repoPath, "git", "fetch", "--all", "--prune");
        }
        return new PreparedRepo(name, repoPath);
    }

    /**
     * Запускает {@code git log --all --numstat} с фиксированным форматом за указанный период.
     */
    public List<String> log(Path repoPath, LocalDateTime since, LocalDateTime until)
            throws IOException, InterruptedException {

        List<String> command = new ArrayList<>(List.of(
                "git", "log", "--all",
                "--pretty=format:" + LOG_FORMAT,
                "--date=iso-strict",
                "--numstat"
        ));
        if (since != null) {
            command.add("--since=" + since);
        }
        if (until != null) {
            command.add("--until=" + until);
        }

        long start = System.currentTimeMillis();
        List<String> result = execute(repoPath, command.toArray(String[]::new));
        log.debug("git log в [{}] вернул {} строк за {} мс",
                repoPath.getFileName(), result.size(), System.currentTimeMillis() - start);
        return result;
    }

    private List<String> execute(Path workingDir, String... command)
            throws IOException, InterruptedException {

        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        if (workingDir != null) {
            pb.directory(workingDir.toFile());
        }
        Process process = pb.start();
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new GitCommandFailedException(Arrays.toString(command), exitCode, lines);
        }
        return lines;
    }

    private String withToken(String repoUrl) {
        String token = properties.token();
        if (token == null || token.isBlank() || !repoUrl.startsWith("https://")) {
            return repoUrl;
        }
        return repoUrl.replace("https://", "https://gitlab-ci-token:" + token + "@");
    }

    /** Результат подготовки локальной копии репозитория. */
    public record PreparedRepo(RepoName name, Path path) {}
}
