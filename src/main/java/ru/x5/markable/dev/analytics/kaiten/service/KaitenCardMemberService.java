package ru.x5.markable.dev.analytics.kaiten.service;

import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenCardMember;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.card.KaitenMemberDto;

import java.util.List;

public interface KaitenCardMemberService {
    void saveCardMembers(Long cardId, List<KaitenMemberDto> members);
    List<KaitenCardMember> getCardMembers(Long cardId);
    List<Long> getCardIdsByUserId(Long userId);
}