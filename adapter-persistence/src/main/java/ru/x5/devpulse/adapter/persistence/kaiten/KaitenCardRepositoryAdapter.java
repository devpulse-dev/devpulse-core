package ru.x5.devpulse.adapter.persistence.kaiten;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.x5.devpulse.application.port.out.KaitenCardRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.kaiten.KaitenCard;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardId;
import ru.x5.devpulse.domain.model.user.KaitenUserId;

@Component
@Log4j2
@RequiredArgsConstructor
class KaitenCardRepositoryAdapter implements KaitenCardRepository {

    private final KaitenCardJpaRepository cards;
    private final KaitenCardMemberJpaRepository members;
    private final KaitenCardEntityMapper mapper;

    @Override
    @Transactional
    public void upsertAll(Collection<KaitenCard> cardsToSave) {
        if (cardsToSave == null || cardsToSave.isEmpty()) return;

        List<KaitenCardEntity> entities = new ArrayList<>(cardsToSave.size());
        for (KaitenCard c : cardsToSave) {
            entities.add(mapper.toEntity(c));
        }
        cards.saveAll(entities);

        // Перезаписываем список членов: delete старых батчем + insert новых
        List<Long> cardIds = cardsToSave.stream().map(c -> c.id().value()).toList();
        members.deleteByCardIdIn(cardIds);

        LocalDateTime now = LocalDateTime.now();
        List<KaitenCardMemberEntity> memberEntities = new ArrayList<>();
        for (KaitenCard c : cardsToSave) {
            for (KaitenUserId memberId : c.memberIds()) {
                memberEntities.add(KaitenCardMemberEntity.builder()
                        .cardId(c.id().value())
                        .userId(memberId.value())
                        .joinedAt(now)
                        .build());
            }
        }
        if (!memberEntities.isEmpty()) {
            members.saveAll(memberEntities);
        }
        log.debug("Upserted {} kaiten cards (+ {} members)", entities.size(), memberEntities.size());
    }

    @Override
    public List<KaitenCard> findByMemberAndPeriod(KaitenUserId memberId, Period period) {
        List<KaitenCardEntity> entities = cards.findByMemberAndPeriod(
                memberId.value(), period.fromAtStartOfDay(), period.toAtEndOfDay());
        if (entities.isEmpty()) return List.of();

        // Одной выборкой подтягиваем членов всех найденных карточек
        Set<Long> ids = new HashSet<>();
        entities.forEach(e -> ids.add(e.getId()));
        Map<Long, List<KaitenUserId>> membersByCard = new HashMap<>();
        for (KaitenCardMemberEntity m : members.findByCardIdIn(ids)) {
            membersByCard
                    .computeIfAbsent(m.getCardId(), k -> new ArrayList<>())
                    .add(new KaitenUserId(m.getUserId()));
        }

        List<KaitenCard> result = new ArrayList<>(entities.size());
        for (KaitenCardEntity e : entities) {
            result.add(mapper.withMembers(
                    e,
                    membersByCard.getOrDefault(e.getId(), List.of())
            ));
        }
        return result;
    }
}
