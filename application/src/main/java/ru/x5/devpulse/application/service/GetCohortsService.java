package ru.x5.devpulse.application.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import ru.x5.devpulse.application.port.in.GetCohortsUseCase;
import ru.x5.devpulse.application.port.out.DailyStatsRepository;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.model.cohort.CohortActivityMatrix;
import ru.x5.devpulse.domain.model.cohort.CohortRetention;
import ru.x5.devpulse.domain.model.cohort.DeveloperActivity;
import ru.x5.devpulse.domain.model.cohort.MonthlyAuthorActivity;
import ru.x5.devpulse.domain.model.cohort.TierTransitions;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.UnifiedUser;
import ru.x5.devpulse.domain.service.CohortAssembler;

/**
 * Реализация {@link GetCohortsUseCase}: SQL-агрегат активности → доменный {@code CohortAssembler}.
 *
 * <p>Ничего не собирает и не ходит в Kaiten — чистое чтение поверх {@code daily_author_stats}.
 * Окно по умолчанию — вся история (GROUP BY в БД возвращает только месяцы с активностью, поэтому
 * широкий нижний bound безвреден). Enrichment матрицы — один batch {@code findByEmails}.</p>
 */
@RequiredArgsConstructor
public final class GetCohortsService implements GetCohortsUseCase {

    private static final int DEFAULT_MIN_COMMITS = 1;
    /** Нижний bound окна по умолчанию: данных раньше нет, GROUP BY вернёт только реальные месяцы. */
    private static final LocalDate FAR_PAST = LocalDate.of(2000, 1, 1);

    private final DailyStatsRepository dailyStatsRepository;
    private final UnifiedUserRepository unifiedUserRepository;
    private final double expectedCommitsPer30Days;

    @Override
    public CohortRetention retention(LocalDate from, LocalDate to, String team, int minCommits) {
        return CohortAssembler.retention(fetch(from, to, team), Math.max(1, minCommits));
    }

    @Override
    public CohortActivityMatrix activityMatrix(LocalDate from, LocalDate to, String team) {
        CohortActivityMatrix raw = CohortAssembler.activityMatrix(fetch(from, to, team), DEFAULT_MIN_COMMITS);
        return enrich(raw);
    }

    @Override
    public TierTransitions tierTransitions(LocalDate from, LocalDate to, String team) {
        return CohortAssembler.tierTransitions(
                fetch(from, to, team), DEFAULT_MIN_COMMITS, expectedCommitsPer30Days);
    }

    private List<MonthlyAuthorActivity> fetch(LocalDate from, LocalDate to, String team) {
        LocalDate fromDate = from != null ? from : FAR_PAST;
        LocalDate toDate = to != null ? to : LocalDate.now();
        String teamFilter = (team == null || team.isBlank()) ? null : team;
        return dailyStatsRepository.monthlyAuthorActivity(fromDate, toDate, teamFilter);
    }

    /** Дозаполняет displayName/avatarUrl/team из unified_user (один batch на всю матрицу). */
    private CohortActivityMatrix enrich(CohortActivityMatrix raw) {
        if (raw.developers().isEmpty()) return raw;

        List<Email> emails = raw.developers().stream().map(DeveloperActivity::email).toList();
        Map<Email, UnifiedUser> profiles = new HashMap<>();
        for (UnifiedUser u : unifiedUserRepository.findByEmails(emails)) {
            profiles.put(u.email(), u);
        }

        List<DeveloperActivity> enriched = new ArrayList<>(raw.developers().size());
        for (DeveloperActivity d : raw.developers()) {
            UnifiedUser u = profiles.get(d.email());
            enriched.add(u == null ? d : d.withProfile(u.name(), u.avatarUrl(), u.team()));
        }
        return new CohortActivityMatrix(raw.months(), enriched);
    }
}
