package ru.x5.markable.dev.analytics.kaiten.service;

import java.util.Collection;
import java.util.Optional;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenCard;

import java.util.List;

/**
 * Сервис для работы с карточками Kaiten.
 * 
 * <p>Предоставляет функциональность для управления карточками Kaiten,
 * включая сохранение, обновление и поиск карточек по идентификаторам.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
public interface KaitenCardService {
    
    /**
     * Сохранить или обновить карточку.
     * 
     * <p>Если карточка с указанным идентификатором существует, она обновляется.
     * В противном случае создаётся новая карточка.</p>
     * 
     * @param card карточка для сохранения или обновления
     * @return сохранённая или обновлённая карточка
     */
    KaitenCard saveOrUpdate(KaitenCard card);
    
    /**
     * Сохранить список карточек.
     * 
     * <p>Сохраняет все карточки из списка. Если карточка с указанным
     * идентификатором существует, она обновляется.</p>
     * 
     * @param cards список карточек для сохранения
     * @return список сохранённых карточек
     */
    List<KaitenCard> saveAll(List<KaitenCard> cards);
    
    /**
     * Получить все карточки.
     * 
     * <p>Возвращает список всех карточек в системе.</p>
     * 
     * @return список всех карточек
     */
    List<KaitenCard> findAll();
    
    /**
     * Найти карточку по идентификатору.
     * 
     * <p>Ищет карточку по указанному идентификатору. Если карточка не найдена,
     * возвращает пустой Optional.</p>
     * 
     * @param id идентификатор карточки
     * @return Optional с карточкой
     */
    Optional<KaitenCard> findById(Long id);
    
    /**
     * Найти карточки по списку идентификаторов.
     * 
     * <p>Возвращает список карточек, идентификаторы которых указаны в списке.</p>
     * 
     * @param ids коллекция идентификаторов карточек
     * @return список найденных карточек
     */
    List<KaitenCard> findByIds(Collection<Long> ids);
}