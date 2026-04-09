package ru.x5.markable.dev.analytics.kaiten.service;

import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenCardResponseDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenStatsResponseDto;

import java.time.LocalDateTime;
import java.util.List;

public interface KaitenStatsService {
    List<KaitenCardResponseDto> getAllCards();
    List<KaitenCardResponseDto> getCardsByDateRange(LocalDateTime from, LocalDateTime to);
    KaitenStatsResponseDto getStats();
}
