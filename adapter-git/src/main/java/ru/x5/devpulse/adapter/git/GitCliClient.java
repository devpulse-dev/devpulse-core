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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
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
 * {@code GIT_ASKPASS}-скрипт ({@link #askpassScript}).</p>
 *
 * <p><b>Streaming и память:</b> {@link #streamLog} читает stdout построчно через {@code Consumer},
 * не накапливая. Это позволяет парсить репозитории с миллионами коммитов без OOM. Метод
 * {@link #log} (eager-вариант) оставлен для совместимости с тестами — на production-пути
 * не используется.</p>
 *
 * <p><b>Timeouts:</b> каждая git-команда ограничена {@link GitProperties#commandTimeout}.
 * По истечении процесс убивается {@code destroyForcibly()}; на это бросается
 * {@link GitCommandFailedException} с {@link GitCommandFailedException#exitCode()}
 * {@value #TIMEOUT_EXIT_CODE}.</p>
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class GitCliClient {

    /** Формат строки заголовка коммита: hash|email|parents|date|message */
    static final String LOG_FORMAT = "%H|%ae|%P|%ad|%s";

    /**
     * Сентинель-значение exit code для timeout-сценария.
     * Реальные git exit codes — неотрицательные целые; -1 для timeout не пересекается.
     */
    static final int TIMEOUT_EXIT_CODE = -1;

    /** Размер ring-buffer'а для хвоста stdout/stderr (для exception message). */
    private static final int TAIL_BUFFER_SIZE = 50;

    /**
     * Сколько ждать после {@code destroyForcibly} перед тем как сдаться.
     * Дольше — пустая трата времени, процесс к этому моменту обычно уже мёртв.
     */
    private static final long DESTROY_GRACE_SECONDS = 5;

    /**
     * Интервал опроса статуса git-процесса. На каждом тике проверяется кооперативная отмена —
     * так git можно убить ВНУТРИ репозитория (не дожидаясь конца команды или {@code commandTimeout}).
     * {@code cancelled} обычно читает флаг из БД, поэтому интервал не слишком мелкий.
     */
    private static final long CANCEL_POLL_MILLIS = 2000;

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
     * Клонирует репозиторий в кеш либо делает {@code git fetch --all --prune}.
     */
    public PreparedRepo prepare(String repoUrl, BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        RepoName name = RepoName.fromUrl(repoUrl);
        Path repoPath = properties.cacheDirectory().resolve(name.value());

        if (Files.notExists(repoPath)) {
            log.info("Клонирую репозиторий [{}]", name);
            executeAndCollect(null, cancelled, "git", "clone", repoUrl, repoPath.toString());
        } else {
            log.info("Обновляю репозиторий [{}]", name);
            executeAndCollect(repoPath, cancelled, "git", "fetch", "--all", "--prune");
        }
        return new PreparedRepo(name, repoPath);
    }

    /**
     * Streaming-режим: каждая строка stdout попадает в {@code lineHandler} сразу.
     *
     * <p>Память O(1): ring-buffer хвоста для exception + ничего больше. Используется
     * {@link GitGatewayAdapter} для парсинга больших репо без накопления.</p>
     */
    public void streamLog(Path repoPath,
                          LocalDateTime since,
                          LocalDateTime until,
                          Consumer<String> lineHandler,
                          BooleanSupplier cancelled) throws IOException, InterruptedException {
        String[] command = buildLogCommand(since, until);
        long start = System.currentTimeMillis();
        executeStreaming(repoPath, command, lineHandler, cancelled);
        log.debug("git log в [{}] завершён за {} мс", repoPath.getFileName(),
                System.currentTimeMillis() - start);
    }

    /**
     * Eager-режим: накапливает весь output в {@code List<String>}.
     *
     * <p><b>Только для тестов / маленьких репо.</b> На production-пути использовать
     * {@link #streamLog} — не упрётся в память.</p>
     */
    public List<String> log(Path repoPath, LocalDateTime since, LocalDateTime until)
            throws IOException, InterruptedException {

        List<String> result = new ArrayList<>();
        streamLog(repoPath, since, until, result::add, () -> false);
        return result;
    }

    private String[] buildLogCommand(LocalDateTime since, LocalDateTime until) {
        List<String> command = new ArrayList<>(List.of(
                "git", "log", "--all",
                "--pretty=format:" + LOG_FORMAT,
                "--date=iso-strict",
                "--numstat"
        ));
        if (since != null) command.add("--since=" + since);
        if (until != null) command.add("--until=" + until);
        return command.toArray(String[]::new);
    }

    /** {@code execute} + сбор stdout в список (для prepare и compatibility). */
    private List<String> executeAndCollect(Path workingDir, BooleanSupplier cancelled, String... command)
            throws IOException, InterruptedException {
        List<String> result = new ArrayList<>();
        executeStreaming(workingDir, command, result::add, cancelled);
        return result;
    }

    /**
     * Базовая операция: запустить git, стримить stdout построчно в {@code lineHandler},
     * ждать с timeout, кинуть {@link GitCommandFailedException} на ненулевом exit code или timeout.
     *
     * <p><b>Почему reader в virtual thread:</b> если бы мы читали stdout в main и потом
     * вызывали {@code waitFor(timeout)}, зависший git без вывода (stuck на DNS, hung HTTPS proxy
     * без ответа) заблокировал бы main на {@code readLine()} навсегда — timeout бы НИКОГДА
     * не сработал. Reader-thread отдаёт main свободу следить за timeout'ом отдельно.</p>
     *
     * <p>Tail последних {@value #TAIL_BUFFER_SIZE} строк сохраняется в ring-buffer и попадает
     * в exception message — для диагностики без удержания всего output в памяти.</p>
     */
    private void executeStreaming(Path workingDir, String[] command, Consumer<String> lineHandler,
                                  BooleanSupplier cancelled)
            throws IOException, InterruptedException {

        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        if (workingDir != null) pb.directory(workingDir.toFile());
        wireCredentials(pb.environment());

        Process process = pb.start();
        Deque<String> tail = new ArrayDeque<>(TAIL_BUFFER_SIZE + 1);

        Thread reader = Thread.ofVirtual()
                .name("git-stdout-reader")
                .start(() -> drainStdout(process, lineHandler, tail));

        long timeoutSeconds = properties.commandTimeout().toSeconds();
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        boolean finished = false;
        try {
            // Опрашиваем процесс короткими интервалами (не один waitFor на весь timeout): на каждом
            // тике проверяем кооперативную отмену — так git убивается ВНУТРИ репо, не дожидаясь
            // конца команды или commandTimeout (гигантский/зависший репо отменяется сразу).
            while (System.nanoTime() < deadlineNanos) {
                if (process.waitFor(CANCEL_POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                    finished = true;
                    break;
                }
                if (cancelled != null && cancelled.getAsBoolean()) {
                    log.info("git отменён оператором — убиваю процесс: {}", Arrays.toString(command));
                    killProcess(process, reader);
                    Thread.currentThread().interrupt();
                    throw new InterruptedException("git отменён оператором");
                }
            }
        } catch (InterruptedException e) {
            // Прерывание потока сбора (или наша отмена выше) — убиваем git и reader, флаг восстановлен.
            killProcess(process, reader);
            throw e;
        }

        if (!finished) {
            log.error("git timeout ({} сек), убиваю процесс: {}",
                    timeoutSeconds, Arrays.toString(command));
            killProcess(process, reader);
            throw new GitCommandFailedException(
                    Arrays.toString(command),
                    TIMEOUT_EXIT_CODE,
                    List.of("[timeout after " + timeoutSeconds + "s]"));
        }

        // Process завершился сам — дожидаемся reader (он близок к концу: stdout закрыт).
        reader.join();

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new GitCommandFailedException(
                    Arrays.toString(command), exitCode, snapshotTail(tail));
        }
    }

    /**
     * Убивает git-процесс и reader-поток best-effort (destroyForcibly + дожидание в пределах grace).
     * Прерывание во время дожидания не пробрасывается — процесс уже помечен на убийство; флаг
     * прерывания восстанавливается.
     */
    private static void killProcess(Process process, Thread reader) {
        process.destroyForcibly();
        reader.interrupt();
        try {
            process.waitFor(DESTROY_GRACE_SECONDS, TimeUnit.SECONDS);
            reader.join(TimeUnit.SECONDS.toMillis(DESTROY_GRACE_SECONDS));
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Читает stdout процесса до EOF, отдаёт каждую строку в {@code lineHandler} и заполняет
     * ring-buffer tail. Запускается в выделенном virtual thread.
     *
     * <p>{@code IOException} (например, при {@code destroyForcibly}) проглатывается:
     * "stdout закрыт" — нормальный путь выхода в timeout-сценарии.</p>
     */
    private static void drainStdout(Process process,
                                    Consumer<String> lineHandler,
                                    Deque<String> tail) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineHandler.accept(line);
                synchronized (tail) {
                    if (tail.size() >= TAIL_BUFFER_SIZE) tail.pollFirst();
                    tail.addLast(line);
                }
            }
        } catch (IOException ignored) {
            // pipe закрыт — обычно после destroyForcibly. Считаем это нормальным выходом.
        }
    }

    /** Snapshot tail под локом — на случай если reader-thread ещё жив (timeout edge case). */
    private static List<String> snapshotTail(Deque<String> tail) {
        synchronized (tail) {
            return new ArrayList<>(tail);
        }
    }

    /**
     * Прокидывает credentials через env vars + askpass:
     * <ul>
     *   <li>{@code GIT_TERMINAL_PROMPT=0} — git не виснет на интерактивном prompt'е;</li>
     *   <li>{@code GIT_ASKPASS=…/devpulse-askpass.sh} — git дёргает скрипт за credentials;</li>
     *   <li>{@code DEVPULSE_GIT_TOKEN=…} — скрипт читает токен из env.</li>
     * </ul>
     */
    private void wireCredentials(Map<String, String> env) {
        env.put("GIT_TERMINAL_PROMPT", "0");
        if (askpassScript != null) {
            env.put("GIT_ASKPASS", askpassScript.toString());
            env.put("DEVPULSE_GIT_TOKEN", properties.token());
        }
    }

    /**
     * Создаёт временный askpass-скрипт с правами 700. POSIX-only.
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
