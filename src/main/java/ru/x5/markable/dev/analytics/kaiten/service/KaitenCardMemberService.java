package ru.x5.markable.dev.analytics.kaiten.service;

import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenCardMember;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenCardDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.card.KaitenMemberDto;

import java.util.List;

/**
 * Сервис для работы с участниками карточек Kaiten.
 * 
 * <p>Предоставляет функциональность для управления участниками карточек,
 * включая сохранение, получение участников и поиск карточек по пользователям.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
public interface KaitenCardMemberService {
    
    /**
     * Сохранить участников карточки.
     * 
     * <p>Сохраняет список участников для указанной карточки.
     * Существующие участники заменяются на новые.</p>
     * 
     * @param cardId идентификатор карточки
     * @param members список участников для сохранения
     */
    void saveCardMembers(Long cardId, List<KaitenMemberDto> members);
    
    /**
     * Получить участников карточки.
     * 
     * <p>Возвращает список всех участников указанной карточки.</p>
     * 
     * @param cardId идентификатор карточки
     * @return список участников карточки
     */
    List<KaitenCardMember> getCardMembers(Long cardId);
    
    /**
     * Получить идентификаторы карточек по идентификатору пользователя.
     *
     * <p>Возвращает список идентификаторов карточек, в которых указанный
     * пользователь является участником.</p>
     *
     * @param userId идентификатор пользователя
     * @return список идентификаторов карточек
     */
    List<Long> getCardIdsByUserId(Long userId);

    /**
     * Сохраняет участников для набора карточек одной транзакцией.
     *
     * <p>Удаляет всех существующих участников для переданных карточек единым DELETE-запросом,
     * затем вставляет новых участников единым batch-INSERT. Это заменяет N пар
     * (deleteByCardId + saveAll) на 1 DELETE + 1 INSERT.</p>
     *
     * @param cards список DTO карточек с участниками
     */
    void saveAllCardMembers(List<KaitenCardDto> cards);
}