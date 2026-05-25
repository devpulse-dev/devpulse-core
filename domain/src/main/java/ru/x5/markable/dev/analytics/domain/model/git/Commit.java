package ru.x5.markable.dev.analytics.domain.model.git;

import java.time.LocalDateTime;
import java.util.Optional;
import ru.x5.markable.dev.analytics.domain.common.TaskNumber;
import ru.x5.markable.dev.analytics.domain.model.user.Email;

/**
 * Один git-коммит, как мы его храним для аналитики.
 *
 * <p>Иммутабельный snapshot: домен только агрегирует поверх него,
 * никогда не мутирует. Сохранение делает {@link
 * ru.x5.markable.dev.analytics.application.port.out.CommitRepository CommitRepository}-адаптер.</p>
 */
public record Commit(
        CommitHash hash,
        Email authorEmail,
        LocalDateTime commitDate,
        boolean merge,
        long addedLines,
        long deletedLines,
        long testAddedLines,
        String message,
        TaskNumber taskNumber,
        RepoName repo
) {

    public Commit {
        if (addedLines < 0 || deletedLines < 0 || testAddedLines < 0) {
            throw new IllegalArgumentException(
                    "line counts must be non-negative: +" + addedLines + " -" + deletedLines
                            + " test+" + testAddedLines);
        }
    }

    public Optional<TaskNumber> task() {
        return Optional.ofNullable(taskNumber);
    }

    public int hour() {
        return commitDate.getHour();
    }
}
