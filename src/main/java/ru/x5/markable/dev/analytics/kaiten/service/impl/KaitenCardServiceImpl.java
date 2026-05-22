package ru.x5.markable.dev.analytics.kaiten.service.impl;

import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenCard;
import ru.x5.markable.dev.analytics.kaiten.persistence.repository.KaitenCardRepository;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenCardService;

import java.util.List;
import java.util.Optional;

/**
 * Сервис для управления карточками Kaiten.
 * 
 * <p>Обеспечивает сохранение, обновление и получение карточек задач
 * из системы Kaiten.</p>
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Сохранение новых карточек</li>
 *   <li>Обновление существующих карточек</li>
 *   <li>Массовое сохранение карточек</li>
 *   <li>Получение карточек по ID</li>
 *   <li>Получение всех карточек</li>
 * </ul>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see KaitenCardService
 * @see KaitenCard
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class KaitenCardServiceImpl implements KaitenCardService {
    private final KaitenCardRepository kaitenCardRepository;

    /**
     * Сохраняет или обновляет карточку.
     * JPA merge обрабатывает upsert по ID без предварительных SELECT-запросов.
     */
    @Override
    @Transactional
    public KaitenCard saveOrUpdate(KaitenCard card) {
        return kaitenCardRepository.save(card);
    }

    /**
     * Сохраняет список карточек.
     * 
     * @param cards список карточек для сохранения
     * @return список сохраненных карточек
     */
    @Override
    @Transactional
    public List<KaitenCard> saveAll(List<KaitenCard> cards) {
        return kaitenCardRepository.saveAll(cards);
    }

    /**
     * Получает все карточки.
     * 
     * @return список всех карточек
     */
    @Override
    public List<KaitenCard> findAll() {
        return kaitenCardRepository.findAll();
    }

    /**
     * Получает карточку по ID.
     * 
     * @param id идентификатор карточки
     * @return Optional с найденной карточкой, или пустой если карточка не найдена
     */
    @Override
    public Optional<KaitenCard> findById(Long id) {
        return kaitenCardRepository.findById(id);
    }

    /**
     * Получает список карточек по списку ID.
     * 
     * @param ids коллекция идентификаторов карточек
     * @return список найденных карточек
     */
    @Override
    public List<KaitenCard> findByIds(Collection<Long> ids) {
        return kaitenCardRepository.findByIds(ids);
    }
}