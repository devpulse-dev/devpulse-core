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
import ru.x5.markable.dev.analytics.kaiten.service.KaitenCardMemberService;
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
    private final KaitenCardService kaitenCardService;
    private final KaitenCardMapper cardMapper;
    private final KaitenSpaceService spaceService;
    private final KaitenUserSyncService kaitenUserSyncService;
    private final UnifiedUserService unifiedUserService;
    private final KaitenProperties properties;
    private final KaitenCardMemberService kaitenCardMemberService;

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
                for (KaitenCardDto cardDto : cards) {
                    // Сохраняем карточку
                    KaitenCard card = cardMapper.toEntity(cardDto);
                    card.setUrl("https://kaiten.x5.ru/" + cardDto.getId());
                    KaitenCard savedCard = kaitenCardService.saveOrUpdate(card);

                    // Сохраняем участников карточки
                    if (cardDto.getMembers() != null && !cardDto.getMembers().isEmpty()) {
                        kaitenCardMemberService.saveCardMembers(savedCard.getId(), cardDto.getMembers());
                    }

                    totalCards++;
                }
                log.info("Space {}: saved {} team cards", space.getId(), cards.size());
            } else {
                log.debug("Space {}: no cards found for team members", space.getId());
            }
        }

        log.info("Total team cards collected: {}", totalCards);
    }

    @Override
    @Transactional
    public void collectCardsForAllUsers(LocalDateTime since) {
        log.info("Collecting Kaiten cards for all users from unified_user");

        // 1. Получаем всех пользователей из unified_user
        List<UnifiedUser> users = unifiedUserService.getAllUsers();
        if (users.isEmpty()) {
            log.warn("No users in unified_user");
            return;
        }

        log.info("Found {} users in unified_user", users.size());

        // 2. Собираем все email для синхронизации
        List<String> allEmails = users.stream()
                .map(UnifiedUser::getEmail)
                .toList();

        // 3. Синхронизируем этих пользователей в kaiten_user (получаем их kaiten_id)
        kaitenUserSyncService.syncUsersByEmails(allEmails);

        // 4. Получаем ID пользователей в Kaiten
        Map<String, Long> userKaitenIds = getUserKaitenIds(allEmails);
        log.info("Found Kaiten IDs for {} users: {}", userKaitenIds.size(), userKaitenIds.values());

        if (userKaitenIds.isEmpty()) {
            log.warn("No users found in Kaiten, skipping cards collection");
            return;
        }

        // 5. Получаем список member_ids для фильтрации на уровне API
        List<Long> memberIds = new ArrayList<>(userKaitenIds.values());

        // 6. Получаем только нужные пространства
        List<KaitenSpaceDto> spaces = getFilteredSpaces();
        log.info("Processing {} spaces", spaces.size());

        int totalCards = 0;
        int updatedCards = 0;

        for (KaitenSpaceDto space : spaces) {
            List<KaitenCardDto> cards = kaitenClient.getCardsBySpace(space.getId(), memberIds, since);

            if (!cards.isEmpty()) {
                for (KaitenCardDto cardDto : cards) {
                    KaitenCard existingCard = kaitenCardService.findById(cardDto.getId()).orElse(null);
                    KaitenCard card = cardMapper.toEntity(cardDto);
                    card.setUrl("https://kaiten.x5.ru/" + cardDto.getId());

                    if (existingCard != null) {
                        card.setId(cardDto.getId());
                        kaitenCardService.saveOrUpdate(card);
                        updatedCards++;
                    } else {
                        kaitenCardService.saveOrUpdate(card);
                        totalCards++;
                    }

                    if (cardDto.getMembers() != null && !cardDto.getMembers().isEmpty()) {
                        kaitenCardMemberService.saveCardMembers(cardDto.getId(), cardDto.getMembers());
                    }
                }
                log.info("Space {}: saved {} cards ({} new, {} updated)",
                        space.getId(), cards.size(), totalCards, updatedCards);
            }
        }

        log.info("Total Kaiten cards collected: {} new, {} updated", totalCards, updatedCards);
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
                unifiedUserService.updateKaitenId(email, userDto.getId(), userDto.getFullName(), userDto.getAvatar());
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
}