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

/**
 * Сервис для получения статистики по карточкам Kaiten.
 * 
 * <p>Обеспечивает получение статистических данных о карточках задач,
 * включая общее количество, статус, среднее время выполнения и комментарии.</p>
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Получение всех карточек</li>
 *   <li>Получение карточек за период</li>
 *   <li>Получение агрегированной статистики</li>
 * </ul>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see KaitenStatsService
 * @see KaitenStatsResponseDto
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class KaitenStatsServiceImpl implements KaitenStatsService {

    private final KaitenCardRepository cardRepository;
    private final KaitenCardCommentRepository commentRepository;
    private final KaitenCardMapper cardMapper;

    /**
     * Получает все карточки.
     * 
     * @return список всех карточек в виде DTO
     */
    @Override
    public List<KaitenCardResponseDto> getAllCards() {
        return cardMapper.toResponseDtoList(cardRepository.findAll());
    }

    /**
     * Получает карточки за указанный период.
     * 
     * @param from начало периода
     * @param to конец периода
     * @return список карточек за период в виде DTO
     */
    @Override
    public List<KaitenCardResponseDto> getCardsByDateRange(LocalDateTime from, LocalDateTime to) {
        return cardMapper.toResponseDtoList(
                cardRepository.findByCreatedAtBetween(from, to)
        );
    }

    /**
     * Получает агрегированную статистику по карточкам.
     * 
     * <p>Включает общее количество карточек, количество закрытых и в работе,
     * среднее время выполнения и общее количество комментариев.</p>
     * 
     * @return DTO с агрегированной статистикой
     */
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
