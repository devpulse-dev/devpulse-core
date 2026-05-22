package ru.x5.markable.dev.analytics.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import ru.x5.markable.dev.analytics.domain.common.PageRequest;
import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.model.git.Commit;
import ru.x5.markable.dev.analytics.domain.model.git.CommitHash;
import ru.x5.markable.dev.analytics.domain.model.user.Email;

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
}
