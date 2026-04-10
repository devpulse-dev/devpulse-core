package ru.x5.markable.dev.analytics.kaiten.service;

import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenSpaceDto;

import java.util.List;

/**
 * Сервис для работы с пространствами Kaiten.
 * 
 * <p>Предоставляет функциональность для получения информации о
 * пространствах (пространствах) в системе Kaiten.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
public interface KaitenSpaceService {
    
    /**
     * Получить все пространства.
     * 
     * <p>Возвращает список всех доступных пространств в системе Kaiten.</p>
     * 
     * @return список DTO пространств
     */
    List<KaitenSpaceDto> getAllSpaces();
}
