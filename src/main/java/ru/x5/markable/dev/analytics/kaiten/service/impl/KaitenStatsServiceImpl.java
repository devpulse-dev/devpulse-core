package ru.x5.markable.dev.analytics.kaiten.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ru.x5.markable.dev.analytics.kaiten.mapper.KaitenCardMapper;
import ru.x5.markable.dev.analytics.kaiten.persistence.repository.KaitenCardCommentRepository;
import ru.x5.markable.dev.analytics.kaiten.persistence.repository.KaitenCardRepository;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenCardResponseDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenStatsResponseDto;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenStatsService;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Log4j2
@RequiredArgsConstructor
public class KaitenStatsServiceImpl implements KaitenStatsService {

    private final KaitenCardRepository cardRepository;
    private final KaitenCardCommentRepository commentRepository;
    private final KaitenCardMapper cardMapper;

    @Override
    public List<KaitenCardResponseDto> getAllCards() {
        return cardMapper.toResponseDtoList(cardRepository.findAll());
    }

    @Override
    public List<KaitenCardResponseDto> getCardsByDateRange(LocalDateTime from, LocalDateTime to) {
        return cardMapper.toResponseDtoList(
                cardRepository.findByCreatedAtBetween(from, to)
        );
    }

    @Override
    public KaitenStatsResponseDto getStats() {
        long totalCards = cardRepository.count();
        long closedCards = cardRepository.countByStatus("Готово");
        long inProgressCards = cardRepository.countByStatus("В работе");
        long openCards = totalCards - closedCards;
        Double avgCompletionHours = cardRepository.getAverageCompletionTimeHours();
        long totalComments = commentRepository.count();

        return KaitenStatsResponseDto.builder()
                .totalCards(totalCards)
                .closedCards(closedCards)
                .inProgressCards(inProgressCards)
                .openCards(openCards)
                .averageCompletionHours(avgCompletionHours)
                .totalComments(totalComments)
                .build();
    }
}
