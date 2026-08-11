package ru.x5.devpulse.domain.model.kaiten;

import java.time.LocalDate;

/**
 * Запись списания времени в Kaiten ({@code GET /time-logs}).
 *
 * <p>Из «толстого» ответа API берём только нужное таймшиту: на какой день списано
 * ({@code for_date}), сколько минут ({@code time_spent}) и на что — карточка (заголовок,
 * тип, ссылка, признак AI-Agent). Полный вложенный card/user в домен не поднимаем.</p>
 *
 * @param date     день, на который списано время (не дата создания лога)
 * @param minutes  списанные минуты (Kaiten хранит время в минутах)
 * @param cardId   карточка, на которую списано время ({@code null} — лог без карточки)
 * @param aiAgent  проставлена ли у карточки галка «AI-Agent»
 */
public record KaitenTimeLog(
        LocalDate date,
        int minutes,
        KaitenCardId cardId,
        String cardTitle,
        String cardUrl,
        KaitenCardType cardType,
        boolean aiAgent) {
}
