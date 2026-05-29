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
     * Стримит коммиты репозитория за период.
     *
     * <p><b>Подготовка локального кеша</b> (clone/fetch) делается адаптером внутри ровно
     * один раз — отдельный метод {@code prepare()} в порту не нужен. Use case не должен
     * знать, что под капотом git CLI и есть какой-то локальный кеш — это implementation detail.</p>
     *
     * @param batchHandler вызывается на каждом батче распарсенных коммитов
     */
    void streamCommits(RepoName repo,
                       LocalDateTime since,
                       LocalDateTime until,
                       Consumer<List<Commit>> batchHandler);
}
