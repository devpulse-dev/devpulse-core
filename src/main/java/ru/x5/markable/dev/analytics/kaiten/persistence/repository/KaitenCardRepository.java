package ru.x5.markable.dev.analytics.kaiten.persistence.repository;

import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenCard;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Репозиторий для работы с сущностью {@link KaitenCard}.
 * 
 * <p>Предоставляет методы для выполнения CRUD операций над записями о карточках Kaiten,
 * а также специализированные методы для поиска по различным критериям и аналитики.</p>
 * 
 * <p>Использует Long в качестве идентификатора сущности.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see KaitenCard
 * @see JpaRepository
 */
@Repository
public interface KaitenCardRepository extends JpaRepository<KaitenCard, Long> {

    /**
     * Находит карточки по идентификатору владельца.
     * 
     * @param ownerId идентификатор владельца карточки
     * @return список карточек владельца
     */
    List<KaitenCard> findByOwnerId(Long ownerId);

    /**
     * Находит карточки, созданные в указанном временном интервале.
     * 
     * @param start начало временного интервала
     * @param end конец временного интервала
     * @return список карточек, созданных в указанном интервале
     */
    List<KaitenCard> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Находит карточки, обновленные после указанной даты.
     * 
     * @param after дата, после которой были обновлены карточки
     * @return список обновленных карточек
     */
    List<KaitenCard> findByUpdatedAtAfter(LocalDateTime after);

    /**
     * Подсчитывает количество карточек с указанным статусом.
     * 
     * @param status статус карточки
     * @return количество карточек с указанным статусом
     */
    @Query("SELECT COUNT(c) FROM KaitenCard c WHERE c.status = :status")
    long countByStatus(@Param("status") String status);

    /**
     * Вычисляет среднее время выполнения карточек в часах.
     * 
     * <p>Использует нативный SQL запрос для вычисления среднего времени между созданием и закрытием карточки.
     * Учитываются только закрытые карточки.</p>
     * 
     * @return среднее время выполнения в часах, или null если нет закрытых карточек
     */
    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (c.closed_at - c.created_at)) / 3600) FROM KaitenCard c WHERE c.closed_at IS NOT NULL", nativeQuery = true)
    Double getAverageCompletionTimeHours();

    /**
     * Находит карточки по списку идентификаторов.
     * 
     * @param ids коллекция идентификаторов карточек
     * @return список карточек с указанными идентификаторами
     */
    @Query("SELECT c FROM KaitenCard c where c.id in (:ids)")
    List<KaitenCard> findByIds(@Param("ids") Collection<Long> ids);
}
