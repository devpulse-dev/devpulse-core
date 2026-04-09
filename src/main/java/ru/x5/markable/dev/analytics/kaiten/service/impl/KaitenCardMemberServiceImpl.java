package ru.x5.markable.dev.analytics.kaiten.service.impl;

import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenCardMember;
import ru.x5.markable.dev.analytics.kaiten.persistence.repository.KaitenCardMemberRepository;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.card.KaitenMemberDto;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenCardMemberService;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Log4j2
@RequiredArgsConstructor
public class KaitenCardMemberServiceImpl implements KaitenCardMemberService {

    private final KaitenCardMemberRepository cardMemberRepository;

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

    @Override
    public List<KaitenCardMember> getCardMembers(Long cardId) {
        return cardMemberRepository.findByCardId(cardId);
    }

    @Override
    public List<Long> getCardIdsByUserId(Long userId) {
        return cardMemberRepository.findCardIdsByUserIdAndPeriod(userId);
    }
}