package ru.x5.devpulse.domain.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ru.x5.devpulse.domain.model.user.Team;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

/**
 * Чистая логика сборки команд из плоского списка {@link UnifiedUser} по полю {@code team}.
 * Лид — участник с {@code lead == true} (берётся первый такой). Stateless, без I/O.
 */
public final class TeamAssembler {

    private TeamAssembler() {}

    /** Группирует пользователей по непустому {@code team} в список {@link Team}, отсортированный по имени. */
    public static List<Team> groupByTeam(List<UnifiedUser> users) {
        Map<String, List<UnifiedUser>> byTeam = new LinkedHashMap<>();
        for (UnifiedUser u : users) {
            String team = u.team();
            if (team == null || team.isBlank()) {
                continue;
            }
            byTeam.computeIfAbsent(team, k -> new ArrayList<>()).add(u);
        }
        List<Team> teams = new ArrayList<>(byTeam.size());
        byTeam.forEach((name, members) -> teams.add(build(name, members)));
        teams.sort(Comparator.comparing(Team::name));
        return teams;
    }

    /** Собирает одну команду: участники отсортированы (лид первым, затем по имени), лид выделен. */
    public static Team build(String name, List<UnifiedUser> members) {
        List<UnifiedUser> sorted = members.stream()
                .sorted(Comparator.comparingInt((UnifiedUser u) -> u.lead() ? 0 : 1)
                        .thenComparing(TeamAssembler::displayKey))
                .toList();
        UnifiedUser lead = members.stream().filter(UnifiedUser::lead).findFirst().orElse(null);
        return new Team(name, lead, sorted);
    }

    private static String displayKey(UnifiedUser u) {
        if (u.name() != null && !u.name().isBlank()) {
            return u.name();
        }
        return u.email() == null ? "" : u.email().value();
    }
}
