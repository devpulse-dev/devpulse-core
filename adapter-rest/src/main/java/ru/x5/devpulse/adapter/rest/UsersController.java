package ru.x5.devpulse.adapter.rest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.x5.devpulse.adapter.rest.api.UsersApi;
import ru.x5.devpulse.adapter.rest.api.model.Commit;
import ru.x5.devpulse.adapter.rest.api.model.SetTeamRequest;
import ru.x5.devpulse.adapter.rest.api.model.UserProfile;
import ru.x5.devpulse.adapter.rest.api.model.UserProfileResponse;
import ru.x5.devpulse.adapter.rest.mapper.CommitMapper;
import ru.x5.devpulse.adapter.rest.mapper.UserProfileMapper;
import ru.x5.devpulse.adapter.rest.mapper.UserProfileResponseMapper;
import ru.x5.devpulse.application.port.in.GetUserCommitsUseCase;
import ru.x5.devpulse.application.port.in.GetUserProfileUseCase;
import ru.x5.devpulse.application.port.in.ListUsersUseCase;
import ru.x5.devpulse.application.port.in.SetUserTeamUseCase;
import ru.x5.devpulse.domain.common.PageRequest;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Эндпоинты пользователя: профиль с агрегированной статистикой и список
 * коммитов с пагинацией. Контракт — {@link UsersApi}, сгенерированный из
 * {@code users-api.yaml}.
 */
@RestController
@RequiredArgsConstructor
class UsersController implements UsersApi {

    private final GetUserProfileUseCase getUserProfile;
    private final GetUserCommitsUseCase getUserCommits;
    private final ListUsersUseCase listUsers;
    private final SetUserTeamUseCase setUserTeam;

    private final UserProfileResponseMapper profileMapper;
    private final CommitMapper commitMapper;
    private final UserProfileMapper userMapper;

    @Override
    public ResponseEntity<UserProfileResponse> getUserProfile(String email,
                                                              LocalDate from, LocalDate to) {
        // Дефолт: последние 30 дней — основной кейс «открыть профиль с главного борда».
        Period period = DashboardController.resolvePeriod(from, to);
        return getUserProfile.findProfile(new Email(email), period)
                .map(profileMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<List<Commit>> getUserCommits(String email,
                                                       LocalDate from, LocalDate to,
                                                       Integer page, Integer size) {
        var data = getUserCommits
                .find(new Email(email), new Period(from, to), new PageRequest(page, size))
                .stream().map(commitMapper::toDto).toList();
        return ResponseEntity.ok(data);
    }

    @Override
    public ResponseEntity<List<UserProfile>> listUsers(String team) {
        var data = listUsers.list(Optional.ofNullable(team).filter(s -> !s.isBlank()))
                .stream().map(userMapper::toDto).toList();
        return ResponseEntity.ok(data);
    }

    @Override
    public ResponseEntity<UserProfile> setUserTeam(String email, SetTeamRequest request) {
        return setUserTeam.setTeam(new Email(email), request.getTeam())
                .map(userMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
