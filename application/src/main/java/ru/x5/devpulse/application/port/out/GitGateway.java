package ru.x5.devpulse.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.git.RepoName;

/**
 * Port out: доступ к git-репозиториям.
 *
 * <p>Реализуется {@code adapter-git} через git CLI.</p>
 */
public interface GitGateway {

    /** Список репозиториев, сконфигурированных к сбору. */
    List<RepoName> configuredRepos();

    /**
     * Подготавливает локальный кеш репозитория (clone или fetch),
     * возвращает идентификатор репозитория для дальнейших операций.
     */
    RepoName prepare(RepoName repo);

    /**
     * Стримит коммиты репозитория за период.
     *
     * <p>Реализация может использовать virtual threads + Structured Concurrency
     * для параллельного fan-out по репозиториям, но это деталь адаптера.</p>
     *
     * @param batchHandler вызывается на каждом батче распарсенных коммитов
     */
    void streamCommits(RepoName repo,
                       LocalDateTime since,
                       LocalDateTime until,
                       Consumer<List<Commit>> batchHandler);
}
