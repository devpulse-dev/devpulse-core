package ru.x5.markable.dev.analytics.gitlab.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.LastExportTracker;
import ru.x5.markable.dev.analytics.gitlab.persistence.repository.LastExportTrackerRepository;
import ru.x5.markable.dev.analytics.gitlab.service.ExportTrackerService;

/**
 * Сервис для отслеживания времени последней выгрузки статистики.
 * 
 * <p>Обеспечивает отслеживание успешных и неудачных выгрузок статистики,
 * сохраняя время последней успешной выгрузки и информацию об ошибках.</p>
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Получение времени последней успешной выгрузки</li>
 *   <li>Отметка успешной выгрузки с обновлением времени</li>
 *   <li>Отметка неудачной выгрузки с сохранением информации об ошибке</li>
 * </ul>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see ExportTrackerService
 * @see LastExportTracker
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class ExportTrackerServiceImpl implements ExportTrackerService {

    private final LastExportTrackerRepository trackerRepository;

    private static final String DAILY_STATS_TYPE = "DAILY_STATS";

    /**
     * Получает время последней успешной выгрузки статистики.
     * 
     * @return Optional с временем последней выгрузки, или пустой если выгрузок не было
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<LocalDateTime> getLastExportTime() {
        return trackerRepository.findLastExportTimeByType(DAILY_STATS_TYPE);
    }

    /**
     * Отмечает успешную выгрузку статистики.
     * 
     * <p>Обновляет время последней выгрузки, устанавливает статус SUCCESS
     * и очищает сообщение об ошибке.</p>
     * 
     * @param exportedUntil время до которого была выгружена статистика
     */
    @Override
    @Transactional
    public void markExportSuccess(LocalDateTime exportedUntil) {
        LocalDateTime now = LocalDateTime.now();

        LastExportTracker tracker = trackerRepository.findByExportType(DAILY_STATS_TYPE)
                .orElse(LastExportTracker.builder()
                        .exportType(DAILY_STATS_TYPE)
                        .createdAt(now)
                        .build());

        tracker.setLastExportTime(exportedUntil);
        tracker.setStatus("SUCCESS");
        tracker.setErrorMessage(null);
        tracker.setUpdatedAt(now);

        trackerRepository.save(tracker);
        log.info("Export marked as successful until: {}", exportedUntil);
    }

    /**
     * Отмечает неудачную выгрузку статистики.
     * 
     * <p>Устанавливает статус FAILED и сохраняет информацию об ошибке
     * с указанием периода выгрузки.</p>
     * 
     * @param start начало периода выгрузки
     * @param end конец периода выгрузки
     * @param errorMessage сообщение об ошибке
     */
    @Override
    @Transactional
    public void markExportFailed(LocalDateTime start, LocalDateTime end, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();

        LastExportTracker tracker = trackerRepository.findByExportType(DAILY_STATS_TYPE)
                .orElse(LastExportTracker.builder()
                        .exportType(DAILY_STATS_TYPE)
                        .createdAt(now)
                        .build());

        tracker.setStatus("FAILED");
        tracker.setErrorMessage(String.format("Period %s - %s: %s", start, end, errorMessage));
        tracker.setUpdatedAt(now);

        trackerRepository.save(tracker);
        log.error("Export failed for period {} - {}. Error: {}", start, end, errorMessage);
    }

}
