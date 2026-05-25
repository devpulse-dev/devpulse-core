package ru.x5.markable.dev.analytics.domain.model.kaiten;

import java.time.LocalDateTime;
import java.util.Optional;
import ru.x5.markable.dev.analytics.domain.model.user.Email;
import ru.x5.markable.dev.analytics.domain.model.user.KaitenUserId;

/**
 * Пользователь Kaiten.
 *
 * <p>{@code email} опционален: некоторые пользователи в Kaiten не имеют email
 * (внешние интеграции / сервисные аккаунты).</p>
 */
public record KaitenUser(
        KaitenUserId id,
        Email email,
        String username,
        String fullName,
        String avatarUrl,
        LocalDateTime lastSyncedAt
) {

    public Optional<Email> emailIfPresent() {
        return Optional.ofNullable(email);
    }
}
