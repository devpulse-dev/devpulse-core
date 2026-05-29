package ru.x5.devpulse.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import ru.x5.devpulse.domain.common.PageRequest;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.git.CommitHash;
import ru.x5.devpulse.domain.model.git.RepoName;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Port out: персистентность коммитов.
 */
public interface CommitRepository {

    /** Какие хеши уже есть в БД — для пропуска дубликатов при batch-вставке. */
    Set<CommitHash> findExistingHashes(Collection<CommitHash> hashes);

    /** Batch-вставка новых коммитов. */
    void saveAll(Collection<Commit> commits);

    /** Список коммитов пользователя за период с пагинацией. */
    List<Commit> findByAuthor(Email email, Period period, PageRequest page);

    /**
     * Все хеши коммитов в указанном репозитории за период.
     *
     * <p>Используется для rebase-cleanup: сравниваем со списком хешей, фактически пришедших
     * из {@code git log} в этом сборе. Хеши которые в БД есть, а в git'е нет — «зомби»
     * после force-push, их удаляем через {@link #deleteByHashes}.</p>
     */
    Set<CommitHash> findHashesByRepoAndPeriod(RepoName repo, Period period);

    /** Bulk-удаление коммитов по хешам. Используется для rebase-cleanup. */
    void deleteByHashes(Collection<CommitHash> hashes);
}
