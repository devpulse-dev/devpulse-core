package ru.x5.markable.dev.analytics.kaiten.service;

import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenCardResponseDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenStatsResponseDto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис для работы со статистикой Kaiten.
 * 
 * <p>Предоставляет функциональность для получения статистики по карточкам
 * и общих метрик системы Kaiten.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
public interface KaitenStatsService {
    
    /**
     * Получить все карточки.
     * 
     * <p>Возвращает список всех карточек в системе Kaiten.</p>
     * 
     * @return список DTO карточек
     */
    List<KaitenCardResponseDto> getAllCards();
    
    /**
     * Получить карточки за период.
     * 
     * <p>Возвращает список карточек, созданных или изменённых в указанный период.</p>
     * 
     * @param from начало периода
     * @param to конец периода
     * @return список DTO карточек за период
     */
    List<KaitenCardResponseDto> getCardsByDateRange(LocalDateTime from, LocalDateTime to);
    
    /**
     * Получить статистику.
     * 
     * <p>Возвращает общую статистику по системе Kaiten, включая количество
     * карточек, пользователей и другие метрики.</p>
     * 
     * @return DTO со статистикой
     */
    KaitenStatsResponseDto getStats();
}
