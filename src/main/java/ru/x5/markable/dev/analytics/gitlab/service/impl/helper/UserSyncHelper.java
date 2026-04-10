package ru.x5.markable.dev.analytics.gitlab.service.impl.helper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.UnifiedUser;
import ru.x5.markable.dev.analytics.gitlab.service.UnifiedUserService;

/**
 * Вспомогательный класс для синхронизации пользователей.
 * Отвечает за создание и кэширование пользователей.
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class UserSyncHelper {

    private final UnifiedUserService unifiedUserService;
    private final Map<String, Object> userCreationLocks = new ConcurrentHashMap<>();

    /**
     * Получает или создаёт пользователя по email.
     * Использует кэш и синхронизацию для предотвращения дубликатов.
     *
     * @param email email пользователя
     * @param userCache кэш пользователей
     * @return ID пользователя
     */
    public Long getOrCreateUserId(String email, Map<String, Long> userCache) {
        return userCache.computeIfAbsent(email, e -> {
            // Синхронизируемся на уровне email, чтобы не создавать одного пользователя дважды
            synchronized (userCreationLocks.computeIfAbsent(e, k -> new Object())) {
                try {
                    UnifiedUser user = unifiedUserService.findOrCreateByEmail(e);
                    return user.getId();
                } finally {
                    userCreationLocks.remove(e);
                }
            }
        });
    }
}
