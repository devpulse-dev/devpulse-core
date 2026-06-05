package ru.x5.devpulse.domain.model.user;

import java.util.List;
import java.util.Objects;

/**
 * Команда — группа {@link UnifiedUser} с общим значением {@code team} и (опционально) лидом.
 *
 * <p>Не хранится отдельной сущностью: деривится из {@code unified_user.team}. Лид — участник
 * с {@code lead == true} (один на команду). Канонический справочник команд — кандидат на Фазу 2.</p>
 *
 * @param lead лид команды; {@code null}, если не назначен
 */
public record Team(String name, UnifiedUser lead, List<UnifiedUser> members) {

    public Team {
        Objects.requireNonNull(name, "team name required");
        members = members == null ? List.of() : List.copyOf(members);
    }
}
