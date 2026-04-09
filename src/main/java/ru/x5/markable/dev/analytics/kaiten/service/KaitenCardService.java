package ru.x5.markable.dev.analytics.kaiten.service;

import java.util.Collection;
import java.util.Optional;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenCard;

import java.util.List;

public interface KaitenCardService {
    KaitenCard save(KaitenCard card);
    List<KaitenCard> saveAll(List<KaitenCard> cards);
    List<KaitenCard> findAll();
    Optional<KaitenCard> findById(Long id);
    List<KaitenCard> findByIds(Collection<Long> ids);
}