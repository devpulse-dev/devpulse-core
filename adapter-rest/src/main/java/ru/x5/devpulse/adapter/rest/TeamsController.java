package ru.x5.devpulse.adapter.rest;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.x5.devpulse.adapter.rest.api.TeamsApi;
import ru.x5.devpulse.adapter.rest.api.model.SetTeamLeadRequest;
import ru.x5.devpulse.adapter.rest.api.model.Team;
import ru.x5.devpulse.adapter.rest.mapper.TeamMapper;
import ru.x5.devpulse.application.port.in.ListTeamsUseCase;
import ru.x5.devpulse.application.port.in.SetTeamLeadUseCase;

/**
 * Команды и их лиды. Контракт — {@link TeamsApi}, сгенерированный из {@code users-api.yaml}
 * (тег Teams). Членство пользователя — через {@code PUT /users/{email}/team} в {@link UsersController}.
 */
@RestController
@RequiredArgsConstructor
class TeamsController implements TeamsApi {

    private final ListTeamsUseCase listTeams;
    private final SetTeamLeadUseCase setTeamLead;
    private final TeamMapper teamMapper;

    @Override
    public ResponseEntity<List<Team>> listTeams() {
        var data = listTeams.list().stream().map(teamMapper::toDto).toList();
        return ResponseEntity.ok(data);
    }

    @Override
    public ResponseEntity<Team> setTeamLead(SetTeamLeadRequest request) {
        return setTeamLead.setLead(request.getTeam(), request.getEmail())
                .map(teamMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
