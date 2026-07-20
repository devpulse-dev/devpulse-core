package ru.x5.devpulse.domain.model.review;

import java.util.Optional;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Вмерженные MR одного автора за период (обогащённый именем/аватаром из {@code unified_user}).
 *
 * <p>{@code displayName}/{@code avatarUrl} могут отсутствовать (не привязан профиль) — обёрнуты
 * в {@link Optional} через геттеры, наружу не отдаём {@code null}.</p>
 */
public record AuthorMergedMrCount(Email email, String displayName, String avatarUrl, int count) {

    public Optional<String> name() {
        return Optional.ofNullable(displayName);
    }

    public Optional<String> avatar() {
        return Optional.ofNullable(avatarUrl);
    }
}
