package ru.x5.devpulse.application.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.x5.devpulse.application.port.in.GetMergedMrStatsUseCase;
import ru.x5.devpulse.application.port.out.ReviewStatsRepository;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.review.AuthorMergedMrCount;
import ru.x5.devpulse.domain.model.review.MergedMrCountRow;
import ru.x5.devpulse.domain.model.review.RepoMergedMrCount;
import ru.x5.devpulse.domain.model.review.TeamMergedMrStats;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

/**
 * Статистика вмерженных MR по команде: команда → email участников → агрегат из БД (GROUP BY author
 * / GROUP BY repo) → обогащение именем/аватаром из тех же {@code unified_user}. Данные историчны
 * (из собранных ревью-метрик), никакого live-fetch.
 *
 * <p>Считаем только MR, вмерженные в «dev»-ветки ({@code devBranches}, конфиг
 * {@code merged-mrs.dev-branches}, по умолчанию dev/main/development).</p>
 */
@Slf4j
@RequiredArgsConstructor
public final class GetMergedMrStatsService implements GetMergedMrStatsUseCase {

    private final UnifiedUserRepository unifiedUserRepository;
    private final ReviewStatsRepository reviewStatsRepository;
    /** Ветки назначения, считающиеся «дев» (target_branch ∈ этого набора). */
    private final List<String> devBranches;

    @Override
    public TeamMergedMrStats get(String team, Period period) {
        // Один findAll (таблица невелика) — и фильтр по команде, и источник имени/аватара.
        Map<Email, UnifiedUser> members = new LinkedHashMap<>();
        for (UnifiedUser u : unifiedUserRepository.findAll()) {
            if (team.equals(u.team())) {
                members.put(u.email(), u);
            }
        }
        if (members.isEmpty()) {
            return new TeamMergedMrStats(team, period, 0, List.of(), List.of());
        }
        if (devBranches.isEmpty()) {
            // Пустой конфиг веток = нечего фильтровать; не считаем «всё подряд» молча.
            log.warn("merged-mrs: пустой merged-mrs.dev-branches — фильтр по ветке не даст результатов");
        }

        List<AuthorMergedMrCount> authors = reviewStatsRepository
                .countMergedMrByAuthor(period, members.keySet(), devBranches).stream()
                .map(row -> enrich(row, members.get(row.email())))
                .sorted(Comparator.comparingInt(AuthorMergedMrCount::count).reversed()
                        .thenComparing(a -> a.email().value()))
                .toList();

        List<RepoMergedMrCount> byRepo = reviewStatsRepository
                .countMergedMrByRepo(period, members.keySet(), devBranches).stream()
                .sorted(Comparator.comparingInt(RepoMergedMrCount::count).reversed()
                        .thenComparing(RepoMergedMrCount::repo))
                .toList();

        // total берём по авторам (тот же набор MR, что и по репам — просто иная группировка).
        int total = authors.stream().mapToInt(AuthorMergedMrCount::count).sum();
        return new TeamMergedMrStats(team, period, total, authors, byRepo);
    }

    private static AuthorMergedMrCount enrich(MergedMrCountRow row, UnifiedUser user) {
        String name = user != null ? user.displayName().orElse(null) : null;
        String avatar = user != null ? user.avatar().orElse(null) : null;
        return new AuthorMergedMrCount(row.email(), name, avatar, (int) row.count());
    }
}
