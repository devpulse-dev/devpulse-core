package ru.x5.markable.dev.analytics.kaiten.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.UnifiedUser;
import ru.x5.markable.dev.analytics.gitlab.service.UnifiedUserService;
import ru.x5.markable.dev.analytics.kaiten.client.KaitenClient;
import ru.x5.markable.dev.analytics.kaiten.config.KaitenProperties;
import ru.x5.markable.dev.analytics.kaiten.mapper.KaitenCardMapper;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenCard;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenSpaceDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenUserDto;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenCardCollectorService;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenCardService;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenSpaceService;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenUserSyncService;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenCardDto;

import java.time.LocalDateTime;

@Service
@Log4j2
@RequiredArgsConstructor
public class KaitenCardCollectorServiceImpl implements KaitenCardCollectorService {

    private final KaitenClient kaitenClient;
    private final KaitenCardService kaitenCardService;      // 👈 вместо cardRepository
    private final KaitenCardMapper cardMapper;
    private final KaitenSpaceService spaceService;
    private final KaitenUserSyncService kaitenUserSyncService;  // 👈 для работы с пользователями Kaiten
    private final UnifiedUserService unifiedUserService;        // 👈 для работы с unified_user
    private final KaitenProperties properties;

    @Override
    public void collectCardsFromAllSpaces(LocalDateTime since) {
        // TODO: реализовать при необходимости
    }

    @Override
    @Transactional
    public void collectCardsForTeam(List<String> teamEmails, LocalDateTime since) {
        log.info("Collecting cards for team of {} members", teamEmails.size());

        // 1. Синхронизируем пользователей в kaiten_user
        kaitenUserSyncService.syncUsersByEmails(teamEmails);

        // 2. Получаем ID пользователей в Kaiten
        Map<String, Long> userKaitenIds = getUserKaitenIds(teamEmails);
        log.info("Found Kaiten IDs for {} users: {}", userKaitenIds.size(), userKaitenIds.values());

        if (userKaitenIds.isEmpty()) {
            log.warn("No users found in Kaiten, skipping cards collection");
            return;
        }

        // 3. Получаем список member_ids для фильтрации на уровне API
        List<Long> memberIds = new ArrayList<>(userKaitenIds.values());

        // 4. Получаем только нужные пространства
        List<KaitenSpaceDto> spaces = getFilteredSpaces();
        log.info("Processing {} spaces", spaces.size());

        int totalCards = 0;

        for (KaitenSpaceDto space : spaces) {
            List<KaitenCardDto> cards = kaitenClient.getCardsBySpace(space.getId(), memberIds, since);

            if (!cards.isEmpty()) {
                List<KaitenCard> teamCards = cards.stream()
                        .map(cardMapper::toEntity)
                        .toList();

                kaitenCardService.saveAll(teamCards);  // 👈 используем сервис
                totalCards += teamCards.size();
                log.info("Space {}: saved {} team cards", space.getId(), teamCards.size());
            } else {
                log.debug("Space {}: no cards found for team members", space.getId());
            }
        }

        log.info("Total team cards collected: {}", totalCards);
    }

    /**
     * Получить Kaiten ID для списка email (с нормализацией регистра)
     */
    private Map<String, Long> getUserKaitenIds(List<String> teamEmails) {
        Map<String, Long> result = new HashMap<>();

        List<String> normalizedEmails = teamEmails.stream()
                .map(String::toLowerCase)
                .toList();

        // 1. Ищем в unified_user
        for (String email : normalizedEmails) {
            Optional<UnifiedUser> user = unifiedUserService.findByEmail(email);
            if (user.isPresent() && user.get().getKaitenId() != null) {
                result.put(email, user.get().getKaitenId());
            }
        }

        // 2. Для остальных ищем в Kaiten по одному
        List<String> missingEmails = normalizedEmails.stream()
                .filter(email -> !result.containsKey(email))
                .toList();

        for (String email : missingEmails) {
            Optional<KaitenUserDto> kaitenUser = kaitenClient.findUserByEmail(email);
            if (kaitenUser.isPresent()) {
                KaitenUserDto userDto = kaitenUser.get();
                result.put(email, userDto.getId());
                unifiedUserService.updateKaitenId(email, userDto.getId(), userDto.getFullName(), userDto.getAvatarInitialsUrl());
            }
        }

        return result;
    }

    /**
     * Получить отфильтрованные пространства (из конфига или все)
     */
    private List<KaitenSpaceDto> getFilteredSpaces() {
        List<KaitenSpaceDto> allSpaces = spaceService.getAllSpaces();

        if (properties.getSpaceIds() != null && !properties.getSpaceIds().isEmpty()) {
            return allSpaces.stream()
                    .filter(space -> properties.getSpaceIds().contains(space.getId()))
                    .toList();
        }

        return allSpaces;
    }

    private void saveCardMembers(Long cardId, List<KaitenCardDto.KaitenMemberDto> members) {
        if (members == null || members.isEmpty()) return;

        List<KaitenCardMember> cardMembers = members.stream()
                .map(m -> KaitenCardMember.builder()
                        .cardId(cardId)
                        .userId(m.getId())
                        .userName(m.getFullName())
                        .userEmail(m.getEmail())
                        .memberType(m.getType())
                        .joinedAt(LocalDateTime.now())
                        .build())
                .toList();

        kaitenCardMemberRepository.saveAll(cardMembers);
    }
}