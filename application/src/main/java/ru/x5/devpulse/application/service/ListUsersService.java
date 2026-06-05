package ru.x5.devpulse.application.service;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import ru.x5.devpulse.application.port.in.ListUsersUseCase;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

/**
 * Список пользователей с опциональным фильтром по команде. Фильтр — в памяти:
 * {@code unified_user} невелик (десятки-сотни записей), отдельный запрос не нужен.
 */
@RequiredArgsConstructor
public final class ListUsersService implements ListUsersUseCase {

    private final UnifiedUserRepository unifiedUserRepository;

    @Override
    public List<UnifiedUser> list(Optional<String> team) {
        List<UnifiedUser> all = unifiedUserRepository.findAll();
        if (team.isEmpty() || team.get().isBlank()) {
            return all;
        }
        String wanted = team.get();
        return all.stream().filter(u -> wanted.equals(u.team())).toList();
    }
}
