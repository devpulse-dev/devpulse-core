package ru.x5.markable.dev.analytics.gitlab.service;

import ru.x5.markable.dev.analytics.gitlab.persistence.entity.UnifiedUser;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.UnifiedUserDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Сервис для работы с унифицированными пользователями.
 * 
 * <p>Предоставляет функциональность для управления пользователями,
 * объединяющими данные из разных систем (GitLab, Kaiten). Позволяет
 * находить, создавать, обновлять и синхронизировать пользователей.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
public interface UnifiedUserService {

    /**
     * Найти или создать пользователя по email.
     * 
     * <p>Ищет пользователя по email. Если пользователь не найден,
     * создаёт новую запись с указанным email.</p>
     * 
     * @param email email пользователя
     * @return найденный или созданный пользователь
     */
    UnifiedUser findOrCreateByEmail(String email);

    /**
     * Получить всех пользователей.
     * 
     * <p>Возвращает список всех унифицированных пользователей в системе.</p>
     * 
     * @return список всех пользователей
     */
    List<UnifiedUser> getAllUsers();

    /**
     * Получить пользователя по email.
     * 
     * <p>Ищет пользователя по email и возвращает его DTO.
     * Если пользователь не найден, возвращает пустой Optional.</p>
     * 
     * @param email email пользователя
     * @return Optional с DTO пользователя
     */
    Optional<UnifiedUserDto> getUserByEmail(String email);

    /**
     * Синхронизировать всех пользователей из Kaiten.
     * 
     * <p>Загружает пользователей из системы Kaiten и обновляет
     * соответствующие записи унифицированных пользователей.</p>
     */
    void syncFromKaiten();

    /**
     * Найти пользователя по email.
     * 
     * <p>Ищет пользователя по email. Если пользователь не найден,
     * возвращает пустой Optional.</p>
     * 
     * @param email email пользователя
     * @return Optional с пользователем
     */
    Optional<UnifiedUser> findByEmail(String email);

    /**
     * Обновить идентификатор Kaiten пользователя.
     * 
     * <p>Обновляет информацию о пользователе, включая идентификатор
     * в системе Kaiten, имя и URL аватара.</p>
     * 
     * @param email email пользователя
     * @param kaitenId идентификатор пользователя в Kaiten
     * @param name имя пользователя
     * @param avatarUrl URL аватара пользователя
     */
    void updateKaitenId(String email, Long kaitenId, String name, String avatarUrl);

    /**
     * Получить всех пользователей с идентификатором Kaiten.
     *
     * <p>Возвращает список пользователей, у которых установлен
     * идентификатор в системе Kaiten.</p>
     *
     * @return список пользователей с идентификатором Kaiten
     */
    List<UnifiedUser> getAllUsersWithKaitenId();

    /**
     * Возвращает карту email → userId для всех указанных email,
     * создавая отсутствующих пользователей одним batch-INSERT.
     *
     * <p>Заменяет N вызовов {@link #findOrCreateByEmail(String)} одним
     * SELECT + одним INSERT для недостающих.</p>
     *
     * @param emails список email (любого регистра)
     * @return карта нормализованный email → userId
     */
    Map<String, Long> findOrCreateAllByEmails(Collection<String> emails);
}
