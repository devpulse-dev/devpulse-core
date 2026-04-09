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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Log4j2
@RequiredArgsConstructor
public class UnifiedUserServiceImpl implements UnifiedUserService {

    private final UnifiedUserRepository unifiedUserRepository;
    private final KaitenUserSyncService kaitenUserSyncService;

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

    @Override
    public Optional<UnifiedUser> findByEmail(String email) {
        String normalizedEmail = email.toLowerCase();
        return unifiedUserRepository.findByEmail(normalizedEmail);
    }

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

    @Override
    public List<UnifiedUserDto> getAllUsers() {
        return unifiedUserRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<UnifiedUserDto> getUserByEmail(String email) {
        String normalizedEmail = email.toLowerCase();
        return unifiedUserRepository.findByEmail(normalizedEmail)
                .map(this::toDto);
    }

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
     * Обновить unified_user данными из Kaiten
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

    private String extractUsername(String email) {
        if (email == null) return "";
        return email.split("@")[0];
    }
}