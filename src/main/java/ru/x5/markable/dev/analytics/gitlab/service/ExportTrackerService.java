package ru.x5.markable.dev.analytics.gitlab.service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Сервис для отслеживания экспорта статистики.
 * 
 * <p>Предоставляет функциональность для отслеживания времени последнего
 * успешного экспорта и записи информации о неудачных попытках экспорта.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
public interface ExportTrackerService {

    /**
     * Получает время последнего успешного экспорта.
     * 
     * <p>Возвращает Optional с датой и временем последнего успешного экспорта.
     * Если экспорт ещё не выполнялся, возвращает пустой Optional.</p>
     * 
     * @return Optional с временем последнего успешного экспорта
     */
    Optional<LocalDateTime> getLastExportTime();

    /**
     * Отмечает успешный экспорт.
     * 
     * <p>Сохраняет информацию о том, что экспорт был успешно выполнен
     * до указанного момента времени.</p>
     * 
     * @param exportedUntil момент времени, до которого был выполнен экспорт
     */
    void markExportSuccess(LocalDateTime exportedUntil);

    /**
     * Отмечает неудачный экспорт.
     * 
     * <p>Сохраняет информацию о неудачной попытке экспорта, включая
     * период и сообщение об ошибке.</p>
     * 
     * @param start начало периода экспорта
     * @param end конец периода экспорта
     * @param errorMessage сообщение об ошибке
     */
    void markExportFailed(LocalDateTime start, LocalDateTime end, String errorMessage);

}
