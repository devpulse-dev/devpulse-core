package ru.x5.devpulse.domain.model.user;

import java.util.Objects;

/**
 * Идентичность пользователя в GitLab (из {@code GET /user}). Ключ матчинга в
 * {@code unified_user} — {@link Email}; {@code gitlabId} линкуется при провижининге.
 *
 * @param name      полное имя из GitLab (может быть null)
 * @param avatarUrl аватар из GitLab (может быть null)
 */
public record GitIdentity(
        Integer gitlabId,
        Email email,
        String username,
        String name,
        String avatarUrl
) {
    public GitIdentity {
        Objects.requireNonNull(gitlabId, "gitlabId required");
        Objects.requireNonNull(email, "email required");
    }
}
