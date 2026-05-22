package ru.x5.markable.dev.analytics.kaiten.service.impl;

import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenCardMember;
import ru.x5.markable.dev.analytics.kaiten.persistence.repository.KaitenCardMemberRepository;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenCardDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.card.KaitenMemberDto;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenCardMemberService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис для управления участниками карточек Kaiten.
 * 
 * <p>Обеспечивает сохранение, обновление и получение информации об участниках
 * карточек задач в системе Kaiten.</p>
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Сохранение участников карточки с заменой старых</li>
 *   <li>Получение списка участников карточки</li>
 *   <li>Получение списка карточек по ID пользователя</li>
 * </ul>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see KaitenCardMemberService
 * @see KaitenCardMember
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class KaitenCardMemberServiceImpl implements KaitenCardMemberService {

    private final KaitenCardMemberRepository cardMemberRepository;

    /**
     * Сохраняет участников карточки.
     * 
     * <p>Удаляет старых участников карточки и сохраняет новых.
     * Используется при обновлении карточки для полной замены списка участников.</p>
     * 
     * @param cardId идентификатор карточки
     * @param members список участников для сохранения
     */
    @Override
    @Transactional
    public void saveCardMembers(Long cardId, List<KaitenMemberDto> members) {
        if (members == null || members.isEmpty()) {
            return;
        }

        // Удаляем старых участников (если карточка обновляется)
        cardMemberRepository.deleteByCardId(cardId);

        // Сохраняем новых
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

        cardMemberRepository.saveAll(cardMembers);
        log.debug("Saved {} members for card {}", cardMembers.size(), cardId);
    }

    /**
     * Получает список участников карточки.
     * 
     * @param cardId идентификатор карточки
     * @return список участников карточки
     */
    @Override
    public List<KaitenCardMember> getCardMembers(Long cardId) {
        return cardMemberRepository.findByCardId(cardId);
    }

    /**
     * Получает список карточек по ID пользователя.
     *
     * @param userId идентификатор пользователя
     * @return список идентификаторов карточек, в которых участвует пользователь
     */
    @Override
    public List<Long> getCardIdsByUserId(Long userId) {
        return cardMemberRepository.findCardIdsByUserIdAndPeriod(userId);
    }

    /**
     * Сохраняет участников для набора карточек одной транзакцией.
     * Один DELETE по всем cardIds + один batch-INSERT всех участников.
     */
    @Override
    @Transactional
    public void saveAllCardMembers(List<KaitenCardDto> cards) {
        List<Long> cardIds = cards.stream().map(KaitenCardDto::getId).toList();

        cardMemberRepository.deleteByCardIdIn(cardIds);

        List<KaitenCardMember> allMembers = new ArrayList<>();
        for (KaitenCardDto card : cards) {
            if (card.getMembers() == null || card.getMembers().isEmpty()) {
                continue;
            }
            for (KaitenMemberDto m : card.getMembers()) {
                allMembers.add(KaitenCardMember.builder()
                        .cardId(card.getId())
                        .userId(m.getId())
                        .userName(m.getFullName())
                        .userEmail(m.getEmail())
                        .memberType(m.getType())
                        .joinedAt(LocalDateTime.now())
                        .build());
            }
        }

        if (!allMembers.isEmpty()) {
            cardMemberRepository.saveAll(allMembers);
        }

        log.debug("Saved members for {} cards ({} member records total)", cardIds.size(), allMembers.size());
    }
}