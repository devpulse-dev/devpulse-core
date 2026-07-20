package ru.x5.devpulse.domain.model.performance;

import java.util.Optional;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Участник карточки дефекта (резолв {@code KaitenUserId → unified_user}) — для аватарок в таблице.
 * {@code displayName}/{@code avatarUrl} могут отсутствовать (наружу не отдаём {@code null} — через геттеры).
 */
public record DefectMember(Email email, String displayName, String avatarUrl) {

    public Optional<String> name() {
        return Optional.ofNullable(displayName);
    }

    public Optional<String> avatar() {
        return Optional.ofNullable(avatarUrl);
    }
}
