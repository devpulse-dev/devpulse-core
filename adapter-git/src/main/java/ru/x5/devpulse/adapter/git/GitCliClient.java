package ru.x5.devpulse.adapter.git;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.x5.devpulse.domain.model.git.RepoName;

/**
 * Тонкая обёртка над git CLI: clone / fetch / log с numstat-форматом.
 *
 * <p>Stateless, потокобезопасен — все методы возвращают результат, не держат состояние.
 * Конкуренция между потоками решается тем, что на каждый репозиторий — своя поддиректория.</p>
 *
 * <p><b>Безопасность токена:</b> токен НЕ попадает в URL, аргументы команды или {@code .git/config}.
 * Передаётся через переменную окружения {@code DEVPULSE_GIT_TOKEN} и одноразовый
 * {@code GIT_ASKPASS}-скрипт ({@link #askpassScript}), который git вызывает при запросе
 * credentials. Так токена нет ни в {@code ps aux}, ни в логах исключений, ни в кешированном
 * git-конфиге после клона.</p>
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class GitCliClient {

    /** Формат строки заголовка коммита: hash|email|parents|date|message */
    static final String LOG_FORMAT = "%H|%ae|%P|%ad|%s";

    private final GitProperties properties;

    /** Путь к одноразовому askpass-скрипту. {@code null} если токен не задан. */
    private Path askpassScript;

    @PostConstruct
    void initAskpass() throws IOException {
        if (properties.token() == null || properties.token().isBlank()) {
            log.info("Git token не задан — приватные репозитории по HTTPS будут недоступны");
            return;
        }
        askpassScript = createAskpassScript();
        log.info("GIT_ASKPASS инициализирован: {}", askpassScript);
    }

    /**
     * Клонирует репозиторий в кеш либо делает {@code git fetch --all --prune}, если он уже есть.
     *
     * @param repoUrl URL репозитория
     * @return локальный путь к репозиторию + извлечённое имя
     */
    public PreparedRepo prepare(String repoUrl) throws IOException, InterruptedException {
        RepoName name = RepoName.fromUrl(repoUrl);
        Path repoPath = properties.cacheDirectory().resolve(name.value());

        if (Files.notExists(repoPath)) {
            log.info("Клонирую репозиторий [{}]", name);
            execute(null, "git", "clone", repoUrl, repoPath.toString());
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
        wireCredentials(pb.environment());

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

    /**
     * Прокидывает credentials через env vars + askpass.
     *
     * <ul>
     *   <li>{@code GIT_TERMINAL_PROMPT=0} — git никогда не повиснет на интерактивном
     *       запросе пароля, если auth не сработает.</li>
     *   <li>{@code GIT_ASKPASS=…/devpulse-askpass.sh} — git дёргает этот скрипт когда ему
     *       нужны credentials; скрипт читает токен из {@code DEVPULSE_GIT_TOKEN}.</li>
     * </ul>
     *
     * <p>Env vars видны только владельцу процесса в {@code /proc/&lt;pid&gt;/environ}, в
     * отличие от {@code ps aux} которое показывает аргументы всем.</p>
     */
    private void wireCredentials(Map<String, String> env) {
        env.put("GIT_TERMINAL_PROMPT", "0");
        if (askpassScript != null) {
            env.put("GIT_ASKPASS", askpassScript.toString());
            env.put("DEVPULSE_GIT_TOKEN", properties.token());
        }
    }

    /**
     * Создаёт временный askpass-скрипт: {@code #!/bin/sh; echo "$DEVPULSE_GIT_TOKEN"}.
     *
     * <p>Права 700 — читать может только владелец процесса. Помечен {@code deleteOnExit}.</p>
     *
     * <p><b>Платформа:</b> POSIX-only (Linux/macOS). На Windows понадобится отдельная реализация
     * через .bat — но проект ориентирован на Linux-инфру и не претендует на cross-platform.</p>
     */
    private static Path createAskpassScript() throws IOException {
        Set<PosixFilePermission> perms = EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE);
        Path script = Files.createTempFile("devpulse-askpass-", ".sh",
                PosixFilePermissions.asFileAttribute(perms));
        Files.writeString(script, "#!/bin/sh\nprintf '%s' \"$DEVPULSE_GIT_TOKEN\"\n");
        script.toFile().deleteOnExit();
        return script;
    }

    /** Результат подготовки локальной копии репозитория. */
    public record PreparedRepo(RepoName name, Path path) {}
}
