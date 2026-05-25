package ru.x5.markable.dev.analytics.adapter.rest.dto;

import java.time.LocalDateTime;
import ru.x5.markable.dev.analytics.domain.model.git.Commit;

/** Один коммит для REST-ответов. */
public record CommitResponse(
        String hash,
        String authorEmail,
        LocalDateTime commitDate,
        boolean merge,
        long addedLines,
        long deletedLines,
        long testAddedLines,
        String message,
        String taskNumber,
        String repo
) {
    public static CommitResponse from(Commit c) {
        return new CommitResponse(
                c.hash().value(),
                c.authorEmail().value(),
                c.commitDate(),
                c.merge(),
                c.addedLines(), c.deletedLines(), c.testAddedLines(),
                c.message(),
                c.task().map(t -> t.value()).orElse(null),
                c.repo().value());
    }
}
