package ru.x5.markable.dev.analytics.kaiten.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenCardComment;

import java.util.List;

/**
 * Репозиторий для работы с сущностью {@link KaitenCardComment}.
 * 
 * <p>Предоставляет методы для выполнения CRUD операций над записями о комментариях к карточкам Kaiten,
 * а также специализированные методы для поиска по идентификатору карточки и идентификатору автора.</p>
 * 
 * <p>Использует Long в качестве идентификатора сущности.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see KaitenCardComment
 * @see JpaRepository
 */
@Repository
public interface KaitenCardCommentRepository extends JpaRepository<KaitenCardComment, Long> {

    /**
     * Находит комментарии к карточке по идентификатору карточки.
     * 
     * @param cardId идентификатор карточки
     * @return список комментариев к карточке
     */
    List<KaitenCardComment> findByCardId(Long cardId);

    /**
     * Находит комментарии по идентификатору автора.
     * 
     * @param authorId идентификатор автора
     * @return список комментариев автора
     */
    List<KaitenCardComment> findByAuthorId(Long authorId);
}
