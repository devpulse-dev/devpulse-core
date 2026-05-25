package ru.x5.markable.dev.analytics.domain.model.stats;

import java.util.Objects;
import ru.x5.markable.dev.analytics.domain.common.Page;
import ru.x5.markable.dev.analytics.domain.common.Period;

/**
 * Главный дашборд: paginated список активных авторов за {@link #period период},
 * отсортирован по убыванию реальной активности (не-мердж коммитов).
 *
 * <p>Старая концепция «outsiders» как отдельной секции убрана: фронт получает последнюю
 * страницу из этого же списка, если хочет показать «хвост».</p>
 *
 * <p>Под «активные» имеются в виду авторы, у которых за период есть хотя бы 1 коммит
 * (включая мерджи). Поле {@code AuthorSummary.nonMergeCommits()} показывает «реальные»
 * не-мердж коммиты, по которым идёт ранжирование.</p>
 */
public record Dashboard(
        Period period,
        Page<AuthorSummary> authors
) {

    public Dashboard {
        Objects.requireNonNull(period, "period required");
        Objects.requireNonNull(authors, "authors required");
    }
}
