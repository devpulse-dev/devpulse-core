package ru.x5.devpulse.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import ru.x5.devpulse.application.port.in.ListTeamsUseCase;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.model.user.Team;
import ru.x5.devpulse.domain.service.TeamAssembler;

/**
 * Список команд: группировка {@code unified_user} по {@code team} (вся таблица невелика —
 * один {@code findAll}). Логика группировки — в чистом {@link TeamAssembler}.
 */
@RequiredArgsConstructor
public final class ListTeamsService implements ListTeamsUseCase {

    private final UnifiedUserRepository unifiedUserRepository;

    @Override
    public List<Team> list() {
        return TeamAssembler.groupByTeam(unifiedUserRepository.findAll());
    }
}
