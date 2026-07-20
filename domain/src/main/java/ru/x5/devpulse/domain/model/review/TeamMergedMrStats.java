package ru.x5.devpulse.domain.model.review;

import java.util.List;
import ru.x5.devpulse.domain.common.Period;

/**
 * Статистика вмерженных MR по команде за период: всего + разбивки по авторам и по репозиториям.
 *
 * <p>Обслуживает {@code GET /api/v2/stats/merged-mrs}. «Вмержен» = {@code merged_at} внутри
 * периода; автор — участник команды. {@code authors} и {@code byRepo} — независимые срезы одного
 * {@code total}, оба отсортированы по убыванию {@code count}.</p>
 */
public record TeamMergedMrStats(
        String team,
        Period period,
        int total,
        List<AuthorMergedMrCount> authors,
        List<RepoMergedMrCount> byRepo) {

    public TeamMergedMrStats {
        authors = authors == null ? List.of() : List.copyOf(authors);
        byRepo = byRepo == null ? List.of() : List.copyOf(byRepo);
    }
}
