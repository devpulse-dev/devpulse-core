package ru.x5.markable.dev.analytics.adapter.persistence.kaiten;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface KaitenCardMemberJpaRepository extends JpaRepository<KaitenCardMemberEntity, Long> {

    List<KaitenCardMemberEntity> findByCardId(Long cardId);

    List<KaitenCardMemberEntity> findByCardIdIn(Collection<Long> cardIds);

    @Modifying
    @Query("delete from KaitenCardMemberEntity m where m.cardId in :cardIds")
    int deleteByCardIdIn(@Param("cardIds") Collection<Long> cardIds);
}
