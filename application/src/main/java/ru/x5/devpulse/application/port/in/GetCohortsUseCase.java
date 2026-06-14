package ru.x5.devpulse.application.port.in;

import java.time.LocalDate;
import ru.x5.devpulse.domain.model.cohort.CohortActivityMatrix;
import ru.x5.devpulse.domain.model.cohort.CohortRetention;
import ru.x5.devpulse.domain.model.cohort.TierTransitions;

/**
 * Когортные/retention-вью по истории коммитов. Окно {@code from/to} опционально (по умолчанию —
 * вся доступная история), {@code team} — фильтр по текущей команде ({@code null}/blank — без фильтра).
 */
public interface GetCohortsUseCase {

    /** Retention-треугольник; {@code minCommits} — порог «активен» в месяце. */
    CohortRetention retention(LocalDate from, LocalDate to, String team, int minCommits);

    /** Матрица «разработчик × месяц» (enriched). */
    CohortActivityMatrix activityMatrix(LocalDate from, LocalDate to, String team);

    /** 4×4 переходы тиров активности месяц-к-месяцу. */
    TierTransitions tierTransitions(LocalDate from, LocalDate to, String team);
}
