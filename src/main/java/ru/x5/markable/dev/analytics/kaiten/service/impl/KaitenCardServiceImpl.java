package ru.x5.markable.dev.analytics.kaiten.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
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
    public KaitenCard save(KaitenCard card) {
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
}