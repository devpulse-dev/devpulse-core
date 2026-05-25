package ru.x5.markable.dev.analytics.adapter.persistence.kaiten;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface KaitenCardJpaRepository extends JpaRepository<KaitenCardEntity, Long> {

    /**
     * Карточки, в которых пользователь — участник, обновлённые в указанном периоде.
     * Join по таблице kaiten_card_member, distinct чтобы не дублировать при нескольких ролях.
     */
    @Query("""
            select distinct c
              from KaitenCardEntity c, KaitenCardMemberEntity m
             where m.cardId = c.id
               and m.userId = :memberId
               and c.updatedAt between :from and :to
            """)
    List<KaitenCardEntity> findByMemberAndPeriod(
            @Param("memberId") Long memberId,
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to);
}
