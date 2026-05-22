package ru.x5.markable.dev.analytics.gitlab.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.UnifiedUser;
import ru.x5.markable.dev.analytics.gitlab.persistence.repository.UnifiedUserRepository;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.UnifiedUserDto;
import ru.x5.markable.dev.analytics.gitlab.service.UnifiedUserService;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenUser;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenUserSyncService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сервис для управления унифицированными пользователями.
 * 
 * <p>Обеспечивает централизованное управление пользователями из разных систем (Git, Kaiten),
 * синхронизацию данных и предотвращение дублирования пользователей.</p>
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Поиск и создание пользователей по email</li>
 *   <li>Синхронизация пользователей из Kaiten</li>
 *   <li>Обновление информации о пользователях</li>
 *   <li>Получение списков пользователей с фильтрацией</li>
 * </ul>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see UnifiedUserService
 * @see UnifiedUser
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class UnifiedUserServiceImpl implements UnifiedUserService {

    private final UnifiedUserRepository unifiedUserRepository;
    private final KaitenUserSyncService kaitenUserSyncService;

    /**
     * Находит или создает пользователя по email.
     * 
     * <p>Если пользователь с указанным email существует, возвращает его.
     * Если нет - создает нового пользователя с нормализованным email.</p>
     * 
     * <p>Метод использует механизм повторных попыток для обработки конкурентных
     * ситуаций при создании пользователя.</p>
     * 
     * @param email email пользователя
     * @return найденный или созданный пользователь
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Retryable(
            value = {DataIntegrityViolationException.class, org.springframework.dao.CannotAcquireLockException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public UnifiedUser findOrCreateByEmail(String email) {
        String normalizedEmail = email.toLowerCase();

        Optional<UnifiedUser> existing = unifiedUserRepository.findByEmail(normalizedEmail);
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            UnifiedUser newUser = UnifiedUser.builder()
                    .email(normalizedEmail)
                    .username(extractUsername(normalizedEmail))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            log.debug("Creating new unified user: {}", normalizedEmail);
            return unifiedUserRepository.save(newUser);

        } catch (DataIntegrityViolationException e) {
            log.debug("User {} was created concurrently, fetching again", normalizedEmail);
            return unifiedUserRepository.findByEmail(normalizedEmail)
                    .orElseThrow(() -> new RuntimeException("Failed to create or find user: " + normalizedEmail, e));
        }
    }

    /**
     * Находит пользователя по email.
     * 
     * @param email email пользователя
     * @return Optional с найденным пользователем, или пустой если пользователь не найден
     */
    @Override
    public Optional<UnifiedUser> findByEmail(String email) {
        String normalizedEmail = email.toLowerCase();
        return unifiedUserRepository.findByEmail(normalizedEmail);
    }

    /**
     * Обновляет информацию о Kaiten для пользователя.
     * 
     * @param email email пользователя
     * @param kaitenId идентификатор пользователя в Kaiten
     * @param name имя пользователя
     * @param avatarUrl URL аватара пользователя
     */
    @Override
    @Transactional
    public void updateKaitenId(String email, Long kaitenId, String name, String avatarUrl) {
        String normalizedEmail = email.toLowerCase();

        unifiedUserRepository.findByEmail(normalizedEmail).ifPresent(user -> {
            user.setKaitenId(kaitenId);
            user.setName(name);
            user.setAvatarUrl(avatarUrl);
            user.setUpdatedAt(LocalDateTime.now());
            unifiedUserRepository.save(user);
            log.debug("Updated unified_user {} with kaiten_id={}", normalizedEmail, kaitenId);
        });
    }

    /**
     * Получает всех пользователей, у которых есть идентификатор в Kaiten.
     * 
     * @return список пользователей с kaitenId
     */
    @Override
    public List<UnifiedUser> getAllUsersWithKaitenId() {
        return unifiedUserRepository.findAll().stream()
                .filter(user -> user.getKaitenId() != null)
                .toList();
    }

    /**
     * Получает всех пользователей.
     *
     * @return список всех пользователей
     */
    @Override
    public List<UnifiedUser> getAllUsers() {
        return unifiedUserRepository.findAll();
    }

    /**
     * Batch find-or-create по списку email: один SELECT + один batch INSERT для недостающих.
     * Возвращает карту нормализованный email → userId.
     */
    @Override
    @Transactional
    public Map<String, Long> findOrCreateAllByEmails(Collection<String> emails) {
        if (emails == null || emails.isEmpty()) {
            return Map.of();
        }

        Set<String> normalized = new HashSet<>();
        for (String e : emails) {
            if (e != null && !e.isBlank()) {
                normalized.add(e.toLowerCase());
            }
        }
        if (normalized.isEmpty()) return Map.of();

        Map<String, Long> result = new HashMap<>(normalized.size());

        // 1 SELECT для всех существующих
        List<UnifiedUser> existing = unifiedUserRepository.findByEmailIn(normalized);
        for (UnifiedUser u : existing) {
            result.put(u.getEmail(), u.getId());
        }

        // Создаём недостающих одним batch-INSERT
        List<UnifiedUser> toCreate = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (String email : normalized) {
            if (!result.containsKey(email)) {
                toCreate.add(UnifiedUser.builder()
                        .email(email)
                        .username(extractUsername(email))
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
            }
        }

        if (!toCreate.isEmpty()) {
            try {
                List<UnifiedUser> saved = unifiedUserRepository.saveAll(toCreate);
                for (UnifiedUser u : saved) {
                    result.put(u.getEmail(), u.getId());
                }
            } catch (DataIntegrityViolationException e) {
                // Конкурирующее создание — перечитаем недостающих
                log.debug("Concurrent insert detected, refetching missing users");
                Set<String> stillMissing = new HashSet<>(normalized);
                stillMissing.removeAll(result.keySet());
                unifiedUserRepository.findByEmailIn(stillMissing)
                        .forEach(u -> result.put(u.getEmail(), u.getId()));
            }
        }

        return result;
    }

    /**
     * Получает пользователя по email в виде DTO.
     * 
     * @param email email пользователя
     * @return Optional с DTO пользователя, или пустой если пользователь не найден
     */
    @Override
    public Optional<UnifiedUserDto> getUserByEmail(String email) {
        String normalizedEmail = email.toLowerCase();
        return unifiedUserRepository.findByEmail(normalizedEmail)
                .map(this::toDto);
    }

    /**
     * Синхронизирует пользователей из Kaiten.
     * 
     * <p>Получает всех пользователей из Kaiten и обновляет соответствующие
     * записи в таблице unified_users.</p>
     */
    @Override
    @Transactional
    public void syncFromKaiten() {
        log.info("Syncing unified users from Kaiten...");

        // ✅ используем сервис KaitenUserSyncService
        List<KaitenUser> kaitenUsers = kaitenUserSyncService.getAllUsers();

        for (KaitenUser kaitenUser : kaitenUsers) {
            updateFromKaiten(kaitenUser);
        }

        log.info("Synced {} users from Kaiten", kaitenUsers.size());
    }

    /**
     * Обновляет unified_user данными из Kaiten.
     * 
     * @param kaitenUser пользователь из Kaiten
     */
    private void updateFromKaiten(KaitenUser kaitenUser) {
        String normalizedEmail = kaitenUser.getEmail().toLowerCase();

        UnifiedUser user = unifiedUserRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> UnifiedUser.builder()
                        .email(normalizedEmail)
                        .createdAt(LocalDateTime.now())
                        .build());

        user.setUsername(kaitenUser.getUsername());
        user.setName(kaitenUser.getName());
        user.setAvatarUrl(kaitenUser.getAvatarUrl());
        user.setKaitenId(kaitenUser.getId());
        user.setLastSyncedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        unifiedUserRepository.save(user);
        log.debug("Updated unified user from Kaiten: {}", normalizedEmail);
    }

    /**
     * Преобразует сущность пользователя в DTO.
     * 
     * @param user сущность пользователя
     * @return DTO пользователя
     */
    private UnifiedUserDto toDto(UnifiedUser user) {
        return UnifiedUserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .kaitenId(user.getKaitenId())
                .gitlabId(user.getGitlabId())
                .build();
    }

    /**
     * Извлекает имя пользователя из email.
     * 
     * @param email email пользователя
     * @return имя пользователя (часть email до @)
     */
    private String extractUsername(String email) {
        if (email == null) return "";
        return email.split("@")[0];
    }
}