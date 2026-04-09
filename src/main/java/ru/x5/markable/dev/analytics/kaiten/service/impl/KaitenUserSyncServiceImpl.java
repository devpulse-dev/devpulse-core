package ru.x5.markable.dev.analytics.kaiten.service.impl;

import java.util.ArrayList;
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

@Service
@Log4j2
@RequiredArgsConstructor
public class KaitenUserSyncServiceImpl implements KaitenUserSyncService {

    private final KaitenClient kaitenClient;
    private final KaitenUserRepository kaitenUserRepository;
    private final KaitenUserMapper kaitenUserMapper;

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

    @Override
    @Transactional
    public void syncUserByEmail(String email) {
        log.info("Syncing Kaiten user by email: {}", email);

        Optional<KaitenUserDto> userOpt = kaitenClient.findUserByEmail(email);

        userOpt.ifPresentOrElse(
                dto -> saveOrUpdate(dto),
                () -> log.warn("User not found in Kaiten: {}", email)
        );
    }

    @Override
    @Transactional
    public void syncUsersByEmails(List<String> emails) {
        log.info("Syncing Kaiten users by {} emails", emails.size());

        for (String email : emails) {
            try {
                Optional<KaitenUserDto> userOpt = kaitenClient.findUserByEmail(email);
                userOpt.ifPresent(this::saveOrUpdate);
            } catch (Exception e) {
                log.error("Failed to sync user: {}", email, e);
            }
        }
    }

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

    @Override
    public Optional<KaitenUser> findByEmail(String email) {
        String normalizedEmail = email.toLowerCase();
        return kaitenUserRepository.findByEmail(normalizedEmail);
    }

    @Override
    public List<KaitenUser> getAllUsers() {
        return kaitenUserRepository.findAll();
    }
}