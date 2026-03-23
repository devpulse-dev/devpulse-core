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

@Component
@Log4j2
@RequiredArgsConstructor
public class GitClient {

    private final GitProperties gitProperties;

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

    private String extractRepoName(String repoUrl) {
        return repoUrl.substring(repoUrl.lastIndexOf("/") + 1)
                .replace(".git", "");
    }
}