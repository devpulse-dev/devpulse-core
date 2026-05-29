package ru.x5.devpulse.adapter.git;

import ru.x5.devpulse.domain.model.git.RepoName;

/**
 * Сбой операции с конкретным репозиторием (clone/fetch/log).
 */
public class GitOperationFailedException extends RuntimeException {
    public GitOperationFailedException(RepoName repo, String message, Throwable cause) {
        super("[" + repo + "] " + message, cause);
    }
}
