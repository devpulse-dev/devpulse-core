package ru.x5.markable.dev.analytics.kaiten.service.impl;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.x5.markable.dev.analytics.kaiten.client.KaitenClient;
import ru.x5.markable.dev.analytics.kaiten.mapper.KaitenUserMapper;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenUser;
import ru.x5.markable.dev.analytics.kaiten.persistence.repository.KaitenUserRepository;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenUserDto;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenUserSyncService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Сервис для синхронизации пользователей из Kaiten.
 * 
 * <p>Обеспечивает синхронизацию пользователей из системы Kaiten в локальную базу данных,
 * поддерживая актуальность информации о пользователях.</p>
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Синхронизация всех пользователей из Kaiten</li>
 *   <li>Синхронизация пользователя по email</li>
 *   <li>Синхронизация списка пользователей по email</li>
 *   <li>Сохранение и обновление пользователей</li>
 *   <li>Поиск пользователей по email</li>
 * </ul>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see KaitenUserSyncService
 * @see KaitenUser
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class KaitenUserSyncServiceImpl implements KaitenUserSyncService {

    private final KaitenClient kaitenClient;
    private final KaitenUserRepository kaitenUserRepository;
    private final KaitenUserMapper kaitenUserMapper;

    /**
     * Синхронизирует всех пользователей из Kaiten.
     * 
     * <p>Получает всех пользователей из Kaiten, преобразует их в сущности
     * и сохраняет в базу данных с обновлением времени синхронизации.</p>
     */
    @Override
    @Transactional
    public void syncAllUsers() {
        log.info("Syncing all Kaiten users...");

        List<KaitenUserDto> userDtos = kaitenClient.getUsers();
        List<KaitenUser> users = kaitenUserMapper.toEntityList(userDtos);

        for (KaitenUser user : users) {
            user.setLastSyncedAt(LocalDateTime.now());
        }

        kaitenUserRepository.saveAll(users);
        log.info("Synced {} Kaiten users", users.size());
    }

    /**
     * Синхронизирует пользователя по email.
     * 
     * <p>Ищет пользователя в Kaiten по email и сохраняет или обновляет его в базе данных.</p>
     * 
     * @param email email пользователя для синхронизации
     */
    @Override
    @Transactional
    public void syncUserByEmail(String email) {
        log.info("Syncing Kaiten user by email: {}", email);

        Optional<KaitenUserDto> userOpt = kaitenClient.findUserByEmail(email);

        userOpt.ifPresentOrElse(
                this::saveOrUpdate,
                () -> log.warn("User not found in Kaiten: {}", email)
        );
    }

    /**
     * Синхронизирует список пользователей по email.
     *
     * <p>Делает один запрос к Kaiten API для получения всех пользователей,
     * затем фильтрует нужные и сохраняет их. Это заменяет N отдельных вызовов.</p>
     *
     * @param emails список email пользователей для синхронизации
     */
    @Override
    @Transactional
    public void syncUsersByEmails(List<String> emails) {
        log.info("Syncing Kaiten users by {} emails", emails.size());

        Set<String> normalizedEmails = emails.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        List<KaitenUserDto> allUsers = kaitenClient.getUsers();

        List<KaitenUserDto> targetUsers = allUsers.stream()
                .filter(u -> u.getEmail() != null
                        && normalizedEmails.contains(u.getEmail().toLowerCase()))
                .toList();

        for (KaitenUserDto dto : targetUsers) {
            try {
                saveOrUpdate(dto);
            } catch (Exception e) {
                log.error("Failed to sync user: {}", dto.getEmail(), e);
            }
        }

        log.info("Synced {} of {} requested users", targetUsers.size(), emails.size());
    }

    /**
     * Сохраняет или обновляет пользователя.
     * 
     * <p>Если пользователь с указанным ID существует, обновляет его данные.
     * Если нет - создает нового пользователя.</p>
     * 
     * @param dto DTO пользователя из Kaiten
     * @return сохраненная или обновленная сущность пользователя
     */
    @Override
    @Transactional
    public KaitenUser saveOrUpdate(KaitenUserDto dto) {
        KaitenUser user = kaitenUserRepository.findById(dto.getId())
                .orElseGet(() -> KaitenUser.builder().id(dto.getId()).build());

        // Обновляем поля через маппер или вручную
        kaitenUserMapper.updateEntity(user, dto);
        user.setLastSyncedAt(LocalDateTime.now());

        return kaitenUserRepository.save(user);
    }

    /**
     * Находит пользователя по email.
     * 
     * @param email email пользователя
     * @return Optional с найденным пользователем, или пустой если пользователь не найден
     */
    @Override
    public Optional<KaitenUser> findByEmail(String email) {
        String normalizedEmail = email.toLowerCase();
        return kaitenUserRepository.findByEmail(normalizedEmail);
    }

    /**
     * Получает всех пользователей.
     * 
     * @return список всех пользователей
     */
    @Override
    public List<KaitenUser> getAllUsers() {
        return kaitenUserRepository.findAll();
    }
}