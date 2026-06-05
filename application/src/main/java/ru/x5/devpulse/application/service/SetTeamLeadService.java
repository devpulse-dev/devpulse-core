package ru.x5.devpulse.application.service;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import ru.x5.devpulse.application.port.in.SetTeamLeadUseCase;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.Team;
import ru.x5.devpulse.domain.model.user.UnifiedUser;
import ru.x5.devpulse.domain.service.TeamAssembler;

/**
 * Назначение/снятие лида команды с инвариантом «один лид на команду».
 *
 * <p>Назначение ({@code email != null}): пользователь добавляется в команду (если ещё не в ней),
 * прежний лид теряет признак, новый — получает. Снятие ({@code email == null}): у всех участников
 * команды снимается признак лида.</p>
 */
@RequiredArgsConstructor
public final class SetTeamLeadService implements SetTeamLeadUseCase {

    private final UnifiedUserRepository unifiedUserRepository;

    @Override
    public Optional<Team> setLead(String team, String email) {
        if (team == null || team.isBlank()) {
            return Optional.empty();
        }

        if (email != null && !email.isBlank()) {
            Email leadEmail = new Email(email);
            if (unifiedUserRepository.findByEmail(leadEmail).isEmpty()) {
                return Optional.empty();   // нет такого пользователя → 404
            }
            unifiedUserRepository.updateTeam(leadEmail, team);   // гарантируем членство
            unifiedUserRepository.clearLeadForTeam(team);        // прежний лид теряет признак
            unifiedUserRepository.updateLead(leadEmail, true);
        } else {
            // Снятие лида: команда должна существовать (иметь участников).
            if (membersOf(team).isEmpty()) {
                return Optional.empty();
            }
            unifiedUserRepository.clearLeadForTeam(team);
        }

        return Optional.of(TeamAssembler.build(team, membersOf(team)));
    }

    private List<UnifiedUser> membersOf(String team) {
        return unifiedUserRepository.findAll().stream()
                .filter(u -> team.equals(u.team()))
                .toList();
    }
}
