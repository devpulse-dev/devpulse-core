package ru.x5.markable.dev.analytics.kaiten.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenCardMember;

import java.util.List;

/**
 * Репозиторий для работы с сущностью {@link KaitenCardMember}.
 * 
 * <p>Предоставляет методы для выполнения CRUD операций над записями о членах карточек Kaiten,
 * а также специализированные методы для поиска по идентификатору карточки и идентификатору пользователя.</p>
 * 
 * <p>Использует Long в качестве идентификатора сущности.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see KaitenCardMember
 * @see JpaRepository
 */
@Repository
public interface KaitenCardMemberRepository extends JpaRepository<KaitenCardMember, Long> {
    
    /**
     * Находит членов карточки по идентификатору карточки.
     * 
     * @param cardId идентификатор карточки
     * @return список членов карточки
     */
    List<KaitenCardMember> findByCardId(Long cardId);
    
    /**
     * Удаляет всех членов карточки по идентификатору карточки.
     * 
     * @param cardId идентификатор карточки
     */
    void deleteByCardId(Long cardId);

    /**
     * Находит идентификаторы карточек по идентификатору пользователя.
     * 
     * <p>Использует JPQL запрос для выборки идентификаторов карточек, в которых пользователь является членом.</p>
     * 
     * @param userId идентификатор пользователя
     * @return список идентификаторов карточек
     */
    @Query("SELECT m.cardId FROM KaitenCardMember m WHERE m.userId = :userId")
    List<Long> findCardIdsByUserIdAndPeriod(@Param("userId") Long userId);
}