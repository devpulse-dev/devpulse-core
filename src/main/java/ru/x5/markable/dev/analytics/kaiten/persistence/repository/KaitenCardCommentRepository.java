package ru.x5.markable.dev.analytics.kaiten.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenCardComment;

import java.util.List;

@Repository
public interface KaitenCardCommentRepository extends JpaRepository<KaitenCardComment, Long> {

    List<KaitenCardComment> findByCardId(Long cardId);

    List<KaitenCardComment> findByAuthorId(Long authorId);
}
