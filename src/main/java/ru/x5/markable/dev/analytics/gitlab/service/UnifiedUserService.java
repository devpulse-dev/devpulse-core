package ru.x5.markable.dev.analytics.gitlab.service;

import ru.x5.markable.dev.analytics.gitlab.persistence.entity.UnifiedUser;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenUser;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.UnifiedUserDto;

import java.util.List;
import java.util.Optional;

public interface UnifiedUserService {

    /**
     * Найти или создать пользователя по email
     */
    UnifiedUser findOrCreateByEmail(String email);

    /**
     * Получить всех пользователей
     */
    List<UnifiedUserDto> getAllUsers();

    /**
     * Получить пользователя по email
     */
    Optional<UnifiedUserDto> getUserByEmail(String email);

    /**
     * Синхронизировать всех пользователей из Kaiten
     */
    void syncFromKaiten();

    Optional<UnifiedUser> findByEmail(String email);
    void updateKaitenId(String email, Long kaitenId, String name, String avatarUrl);
}
