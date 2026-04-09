package ru.x5.markable.dev.analytics.kaiten.persistence.repository;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenCardMember;

import java.util.List;

@Repository
public interface KaitenCardMemberRepository extends JpaRepository<KaitenCardMember, Long> {
    List<KaitenCardMember> findByCardId(Long cardId);
    void deleteByCardId(Long cardId);

    @Query("SELECT m.cardId FROM KaitenCardMember m WHERE m.userId = :userId")
    List<Long> findCardIdsByUserIdAndPeriod(@Param("userId") Long userId);
}