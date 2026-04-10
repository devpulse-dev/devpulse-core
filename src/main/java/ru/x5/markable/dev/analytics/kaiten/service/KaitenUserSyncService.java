package ru.x5.markable.dev.analytics.kaiten.service;

import java.util.List;
import java.util.Optional;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenUser;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenUserDto;

/**
 * Сервис для синхронизации пользователей Kaiten.
 * 
 * <p>Предоставляет функциональность для синхронизации пользователей из системы Kaiten
 * с локальной базой данных, а также для поиска и управления пользователями.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
public interface KaitenUserSyncService {

    /**
     * Синхронизировать всех пользователей.
     * 
     * <p>Загружает всех пользователей из системы Kaiten и сохраняет их в локальной базе данных.
     * Если пользователь уже существует, обновляет его данные.</p>
     */
    void syncAllUsers();
    
    /**
     * Синхронизировать пользователя по email.
     * 
     * <p>Загружает пользователя с указанным email из системы Kaiten и сохраняет его
     * в локальной базе данных. Если пользователь уже существует, обновляет его данные.</p>
     * 
     * @param email email пользователя для синхронизации
     */
    void syncUserByEmail(String email);

    /**
     * Синхронизировать пользователей по списку email.
     * 
     * <p>Загружает пользователей с указанными email из системы Kaiten и сохраняет их
     * в локальной базе данных. Если пользователь уже существует, обновляет его данные.</p>
     * 
     * @param emails список email пользователей для синхронизации
     */
    void syncUsersByEmails(List<String> emails);
    
    /**
     * Сохранить или обновить пользователя.
     * 
     * <p>Сохраняет нового пользователя или обновляет существующего на основе данных из DTO.</p>
     * 
     * @param dto DTO с данными пользователя
     * @return сохранённая сущность пользователя
     */
    KaitenUser saveOrUpdate(KaitenUserDto dto);

    /**
     * Найти пользователя по email.
     * 
     * <p>Ищет пользователя в локальной базе данных по указанному email.</p>
     * 
     * @param email email пользователя для поиска
     * @return Optional с найденным пользователем или пустой, если пользователь не найден
     */
    Optional<KaitenUser> findByEmail(String email);

    /**
     * Получить всех пользователей.
     * 
     * <p>Возвращает список всех пользователей из локальной базы данных.</p>
     * 
     * @return список всех пользователей
     */
    List<KaitenUser> getAllUsers();

}
