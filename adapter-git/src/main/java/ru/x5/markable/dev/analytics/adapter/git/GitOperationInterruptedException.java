package ru.x5.markable.dev.analytics.adapter.git;

import ru.x5.markable.dev.analytics.domain.model.git.RepoName;

/**
 * Git-операция была прервана (interrupt при ожидании дочернего процесса).
 *
 * <p>Отделена от {@link GitOperationFailedException}, чтобы application мог решить
 * остановить весь fan-out, а не падать на одиночной ошибке репозитория.</p>
 */
public class GitOperationInterruptedException extends RuntimeException {
    public GitOperationInterruptedException(RepoName repo, Throwable cause) {
        super("[" + repo + "] прервано", cause);
    }
}
