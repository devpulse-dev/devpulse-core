package ru.x5.markable.dev.analytics.kaiten.rest.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KaitenStatsResponseDto {
    private long totalCards;
    private long closedCards;
    private long inProgressCards;
    private long openCards;
    private Double averageCompletionHours;
    private long totalComments;
}
