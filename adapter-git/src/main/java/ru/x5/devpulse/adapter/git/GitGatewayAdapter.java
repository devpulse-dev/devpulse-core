package ru.x5.devpulse.adapter.git;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.x5.devpulse.application.port.out.GitGateway;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.git.RepoName;

/**
 * Реализация {@link GitGateway} поверх локального git CLI.
 *
 * <p>I/O-операции (fork/exec git, чтение stdout) — естественные кандидаты для virtual threads,
 * но fan-out по репозиториям делает не сам адаптер — это работа application-слоя
 * (use case CollectDailyStats). Адаптер просто выдаёт коммиты по одному репозиторию за раз.</p>
 *
 * <p>Текущая реализация передаёт ВСЕ коммиты репозитория одной партией через {@code batchHandler}.
 * Если репозитории будут расти и память станет проблемой — здесь легко переключиться на
 * чтение stdout построчно и flush'ить пакеты по N коммитов.</p>
 */
@Component
@Log4j2
@RequiredArgsConstructor
class GitGatewayAdapter implements GitGateway {

    private final GitProperties properties;
    private final GitCliClient cli;

    @Override
    public List<RepoName> configuredRepos() {
        return properties.repositories().stream()
                .map(RepoName::fromUrl)
                .toList();
    }

    @Override
    public RepoName prepare(RepoName repo) {
        String url = findUrl(repo);
        try {
            GitCliClient.PreparedRepo prepared = cli.prepare(url);
            return prepared.name();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitOperationInterruptedException(repo, e);
        } catch (Exception e) {
            throw new GitOperationFailedException(repo, "не удалось подготовить репозиторий", e);
        }
    }

    @Override
    public void streamCommits(RepoName repo,
                              LocalDateTime since,
                              LocalDateTime until,
                              Consumer<List<Commit>> batchHandler) {
        String url = findUrl(repo);
        try {
            GitCliClient.PreparedRepo prepared = cli.prepare(url);
            List<String> rawLines = cli.log(prepared.path(), since, until);
            List<Commit> commits = GitLogParser.parse(rawLines, prepared.name());

            if (commits.isEmpty()) {
                log.debug("В репозитории {} нет коммитов за период {} – {}", repo, since, until);
                return;
            }
            // Текущий API отдаёт всё одним батчем. Это сохраняет совместимость с тем как
            // application-слой ожидает: при необходимости — разбить на чанки тут.
            batchHandler.accept(commits);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitOperationInterruptedException(repo, e);
        } catch (Exception e) {
            throw new GitOperationFailedException(repo, "не удалось собрать коммиты", e);
        }
    }

    /** Сопоставляет {@link RepoName} с одним из URL'ов в конфиге. */
    private String findUrl(RepoName repo) {
        return properties.repositories().stream()
                .filter(url -> RepoName.fromUrl(url).equals(repo))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "репозиторий " + repo + " не найден в конфигурации git.repositories"));
    }
}
