package ru.x5.devpulse.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import ru.x5.devpulse.application.port.in.SetUserTeamUseCase;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

/**
 * Назначение/снятие команды пользователя. Проверяем существование (для 404 на REST-границе),
 * затем обновляем и возвращаем свежую запись.
 */
@RequiredArgsConstructor
public final class SetUserTeamService implements SetUserTeamUseCase {

    private final UnifiedUserRepository unifiedUserRepository;

    @Override
    public Optional<UnifiedUser> setTeam(Email email, String team) {
        if (unifiedUserRepository.findByEmail(email).isEmpty()) {
            return Optional.empty();
        }
        unifiedUserRepository.updateTeam(email, team);
        return unifiedUserRepository.findByEmail(email);
    }
}
