package ru.x5.markable.dev.analytics.kaiten.rest.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO ответа со статистикой Kaiten.
 * 
 * <p>Содержит статистическую информацию о карточках Kaiten, включая общее количество,
 * количество закрытых, в работе и открытых карточек, среднее время выполнения
 * и общее количество комментариев.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
@Builder
public class KaitenStatsResponseDto {
    
    /**
     * Общее количество карточек.
     * 
     * <p>Общее количество карточек в системе Kaiten.</p>
     */
    private long totalCards;
    
    /**
     * Количество закрытых карточек.
     * 
     * <p>Количество карточек, которые были закрыты.</p>
     */
    private long closedCards;
    
    /**
     * Количество карточек в работе.
     * 
     * <p>Количество карточек, которые находятся в работе.</p>
     */
    private long inProgressCards;
    
    /**
     * Количество открытых карточек.
     * 
     * <p>Количество открытых карточек.</p>
     */
    private long openCards;
    
    /**
     * Среднее время выполнения в часах.
     * 
     * <p>Среднее время выполнения карточек в часах.</p>
     */
    private Double averageCompletionHours;
    
    /**
     * Общее количество комментариев.
     * 
     * <p>Общее количество комментариев ко всем карточкам.</p>
     */
    private long totalComments;
}
