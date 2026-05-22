package ru.x5.markable.dev.analytics.kaiten.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.UnifiedUser;
import ru.x5.markable.dev.analytics.gitlab.service.UnifiedUserService;
import ru.x5.markable.dev.analytics.kaiten.client.KaitenClient;
import ru.x5.markable.dev.analytics.kaiten.mapper.KaitenCardMapper;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenCard;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenCardDto;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenCardCollectorService;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenCardMemberService;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenCardService;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenUserSyncService;

import java.time.LocalDateTime;

/**
 * Сервис для сбора карточек из Kaiten.
 * 
 * <p>Обеспечивает сбор карточек задач из системы Kaiten для указанных пользователей
 * или команд, синхронизацию пользователей и сохранение карточек в базу данных.</p>
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Сбор карточек для команды пользователей</li>
 *   <li>Сбор карточек для всех пользователей из unified_user</li>
 *   <li>Синхронизация пользователей между системами</li>
 *   <li>Фильтрация пространств по конфигурации</li>
 *   <li>Сохранение участников карточек</li>
 * </ul>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see KaitenCardCollectorService
 * @see KaitenCard
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class KaitenCardCollectorServiceImpl implements KaitenCardCollectorService {

    private final KaitenClient kaitenClient;
    private final KaitenCardService kaitenCardService;
    private final KaitenCardMapper cardMapper;
    private final KaitenUserSyncService kaitenUserSyncService;
    private final UnifiedUserService unifiedUserService;
    private final KaitenCardMemberService kaitenCardMemberService;

    /**
     * Собирает карточки из всех пространств.
     * 
     * @param since начало периода сбора карточек
     */
    @Override
    public void collectCardsFromAllSpaces(LocalDateTime since) {
        // TODO: реализовать при необходимости
    }

    /**
     * Собирает карточки для команды пользователей.
     *
     * <p>Без @Transactional: каждая страница карточек коммитится своей внутренней транзакцией.
     * Если позже упадём по rate-limit — уже сохранённые страницы НЕ откатываются.</p>
     */
    @Override
    public void collectCardsForTeam(List<String> teamEmails, LocalDateTime since) {
        log.info("Collecting cards for team of {} members", teamEmails.size());

        kaitenUserSyncService.syncUsersByEmails(teamEmails);

        Map<String, Long> userKaitenIds = getUserKaitenIds(teamEmails);
        log.info("Found Kaiten IDs for {} users: {}", userKaitenIds.size(), userKaitenIds.values());

        if (userKaitenIds.isEmpty()) {
            log.warn("No users found in Kaiten, skipping cards collection");
            return;
        }

        List<Long> memberIds = List.copyOf(userKaitenIds.values());

        // Стрим: каждая страница сохраняется отдельной транзакцией
        kaitenClient.streamCards(memberIds, since, this::persistCards);
    }

    /**
     * Собирает карточки для всех пользователей из unified_user.
     *
     * <p>Без @Transactional — см. {@link #collectCardsForTeam}.</p>
     */
    @Override
    public void collectCardsForAllUsers(LocalDateTime since) {
        log.info("Collecting Kaiten cards for all users from unified_user");

        List<UnifiedUser> users = unifiedUserService.getAllUsers();
        if (users.isEmpty()) {
            log.warn("No users in unified_user");
            return;
        }
        log.info("Found {} users in unified_user", users.size());

        List<String> allEmails = users.stream().map(UnifiedUser::getEmail).toList();

        kaitenUserSyncService.syncUsersByEmails(allEmails);

        Map<String, Long> userKaitenIds = getUserKaitenIds(allEmails);
        log.info("Found Kaiten IDs for {} users", userKaitenIds.size());

        if (userKaitenIds.isEmpty()) {
            log.warn("No users found in Kaiten, skipping cards collection");
            return;
        }

        List<Long> memberIds = List.copyOf(userKaitenIds.values());

        // Стрим: каждая страница сохраняется отдельной транзакцией.
        // Если позже упадём по 429 — уже сохранённые страницы останутся в БД.
        kaitenClient.streamCards(memberIds, since, this::persistCards);
    }

    /**
     * Сохраняет страницу карточек и их участников батч-операциями.
     *
     * <p>Каждая страница сохраняется отдельной транзакцией: даже если последующая
     * страница упадёт по rate-limit, уже сохранённые останутся в БД.</p>
     *
     * <p>Внутри: 1 batch SELECT (findByIds) + 1 saveAll (карточки) + 1 DELETE + 1 INSERT (участники).</p>
     */
    private void persistCards(List<KaitenCardDto> cardDtos) {
        if (cardDtos.isEmpty()) return;

        List<Long> cardIds = cardDtos.stream().map(KaitenCardDto::getId).toList();

        Set<Long> existingIds = kaitenCardService.findByIds(cardIds).stream()
                .map(KaitenCard::getId)
                .collect(Collectors.toSet());

        List<KaitenCard> cards = cardDtos.stream()
                .map(dto -> {
                    KaitenCard card = cardMapper.toEntity(dto);
                    card.setUrl("https://kaiten.x5.ru/" + dto.getId());
                    return card;
                })
                .toList();

        kaitenCardService.saveAll(cards);
        kaitenCardMemberService.saveAllCardMembers(cardDtos);

        long newCount = cardIds.stream().filter(id -> !existingIds.contains(id)).count();
        log.info("Persisted page: {} cards ({} new, {} updated)",
                cards.size(), newCount, cards.size() - newCount);
    }

    /**
     * Возвращает карту email → Kaiten ID для указанных пользователей.
     *
     * <p>Сначала читает из unified_user одним batch-запросом.
     * Для ненайденных смотрит в kaiten_user (уже синхронизирован) — без API-вызовов.</p>
     */
    private Map<String, Long> getUserKaitenIds(List<String> teamEmails) {
        Map<String, Long> result = new HashMap<>();

        List<String> normalizedEmails = teamEmails.stream()
                .map(String::toLowerCase)
                .toList();

        // 1 DB-запрос вместо N findByEmail
        unifiedUserService.getAllUsersWithKaitenId().stream()
                .filter(u -> normalizedEmails.contains(u.getEmail().toLowerCase()))
                .forEach(u -> result.put(u.getEmail().toLowerCase(), u.getKaitenId()));

        // Для оставшихся — ищем в kaiten_user (уже синхронизирован, без API)
        normalizedEmails.stream()
                .filter(email -> !result.containsKey(email))
                .forEach(email -> kaitenUserSyncService.findByEmail(email).ifPresent(ku -> {
                    result.put(email, ku.getId());
                    unifiedUserService.updateKaitenId(
                            email, ku.getId(), ku.getName(), ku.getAvatarUrl());
                }));

        return result;
    }
}