package ru.x5.devpulse.domain.model.cohort;

import java.time.YearMonth;
import java.util.List;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Строка матрицы активности: один разработчик × месяцы.
 *
 * <p>{@code cells} выровнены по оси месяцев {@code CohortActivityMatrix.months} — не-мердж
 * коммиты за каждый месяц (0 = неактивен). {@code displayName}/{@code avatarUrl}/{@code team}
 * дозаполняются enrichment'ом в use case (как у {@code AuthorSummary}).</p>
 */
public record DeveloperActivity(
        Email email,
        String displayName,
        String avatarUrl,
        String team,
        YearMonth firstActive,
        YearMonth lastActive,
        List<Integer> cells
) {

    /** Копия с профильными полями из {@code unified_user}. */
    public DeveloperActivity withProfile(String displayName, String avatarUrl, String team) {
        return new DeveloperActivity(email, displayName, avatarUrl, team, firstActive, lastActive, cells);
    }
}
