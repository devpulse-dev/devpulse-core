package ru.x5.markable.dev.analytics.kaiten.service.impl;

import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenCard;
import ru.x5.markable.dev.analytics.kaiten.persistence.repository.KaitenCardRepository;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenCardService;

import java.util.List;
import java.util.Optional;

@Service
@Log4j2
@RequiredArgsConstructor
public class KaitenCardServiceImpl implements KaitenCardService {

    private final KaitenCardRepository kaitenCardRepository;

    @Override
    @Transactional
    public KaitenCard saveOrUpdate(KaitenCard card) {
        // Если карточка существует, обновляем
        if (card.getId() != null && kaitenCardRepository.existsById(card.getId())) {
            KaitenCard existing = kaitenCardRepository.findById(card.getId()).get();
            existing.setTitle(card.getTitle());
            existing.setDescription(card.getDescription());
            existing.setStatus(card.getStatus());
            existing.setPriority(card.getPriority());
            existing.setSpaceId(card.getSpaceId());
            existing.setSpaceName(card.getSpaceName());
            existing.setBoardId(card.getBoardId());
            existing.setBoardName(card.getBoardName());
            existing.setOwnerId(card.getOwnerId());
            existing.setOwnerName(card.getOwnerName());
            existing.setTypeId(card.getTypeId());
            existing.setTypeName(card.getTypeName());
            existing.setColumnId(card.getColumnId());
            existing.setColumnName(card.getColumnName());
            existing.setLaneId(card.getLaneId());
            existing.setLaneName(card.getLaneName());
            existing.setUpdatedAt(card.getUpdatedAt());
            existing.setClosedAt(card.getClosedAt());
            existing.setLastMovedAt(card.getLastMovedAt());
            existing.setLaneChangedAt(card.getLaneChangedAt());
            existing.setArchived(card.getArchived());
            existing.setTags(card.getTags());
            existing.setCustomFields(card.getCustomFields());
            existing.setUrl(card.getUrl());
            existing.setVersion(card.getVersion());
            return kaitenCardRepository.save(existing);
        }

        // Новая карточка
        return kaitenCardRepository.save(card);
    }

    @Override
    public List<KaitenCard> saveAll(List<KaitenCard> cards) {
        return kaitenCardRepository.saveAll(cards);
    }

    @Override
    public List<KaitenCard> findAll() {
        return kaitenCardRepository.findAll();
    }

    @Override
    public Optional<KaitenCard> findById(Long id) {
        return kaitenCardRepository.findById(id);
    }

    @Override
    public List<KaitenCard> findByIds(Collection<Long> ids) {
        return kaitenCardRepository.findByIds(ids);
    }
}