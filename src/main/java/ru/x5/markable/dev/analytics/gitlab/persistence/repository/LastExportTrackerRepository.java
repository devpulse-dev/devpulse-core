package ru.x5.markable.dev.analytics.gitlab.persistence.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.LastExportTracker;

/**
 * Репозиторий для работы с сущностью {@link LastExportTracker}.
 * 
 * <p>Предоставляет методы для выполнения CRUD операций над записями о времени последнего экспорта,
 * а также специализированные методы для поиска по типу экспорта.</p>
 * 
 * <p>Использует UUID в качестве идентификатора сущности.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see LastExportTracker
 * @see JpaRepository
 */
@Repository
public interface LastExportTrackerRepository extends JpaRepository<LastExportTracker, UUID> {

    /**
     * Находит запись о времени последнего экспорта по типу экспорта.
     * 
     * @param exportType тип экспорта
     * @return запись о времени последнего экспорта или {@link Optional#empty()}, если запись не найдена
     */
    Optional<LastExportTracker> findByExportType(String exportType);

    /**
     * Находит время последнего экспорта по типу экспорта.
     * 
     * @param type тип экспорта
     * @return время последнего экспорта или {@link Optional#empty()}, если запись не найдена
     */
    @Query("SELECT l.lastExportTime FROM LastExportTracker l WHERE l.exportType = :type")
    Optional<LocalDateTime> findLastExportTimeByType(@Param("type") String type);

}
