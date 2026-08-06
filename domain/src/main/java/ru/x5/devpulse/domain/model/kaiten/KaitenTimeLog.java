package ru.x5.devpulse.domain.model.kaiten;

import java.time.LocalDate;

/**
 * Запись списания времени в Kaiten ({@code GET /time-logs}).
 *
 * <p>Из «толстого» ответа API берём только то, что нужно домену: на какой день списано
 * ({@code for_date}) и сколько минут ({@code time_spent}). Вложенные {@code card}/{@code user}
 * в доменную модель не поднимаем — ответ Kaiten весит мегабайты, а таймшиту они не нужны.</p>
 *
 * @param date    день, на который списано время (не дата создания лога)
 * @param minutes списанные минуты (Kaiten хранит время в минутах)
 */
public record KaitenTimeLog(LocalDate date, int minutes) {
}
