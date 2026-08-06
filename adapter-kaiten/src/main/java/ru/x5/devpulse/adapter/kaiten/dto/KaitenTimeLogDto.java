package ru.x5.devpulse.adapter.kaiten.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * Запись списания времени, как её возвращает {@code GET /time-logs} Kaiten.
 *
 * <p>Ответ API «толстый»: в каждом логе едут вложенные объекты {@code card} и {@code user}
 * целиком (100 логов ≈ несколько МБ). Берём только нужные скаляры — Jackson игнорирует
 * остальное ({@link JsonIgnoreProperties}), и в память не поднимается лишнее.</p>
 *
 * @param timeSpent списанные минуты
 * @param forDate   день, на который списано время (не дата создания записи)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KaitenTimeLogDto(
        long id,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("card_id") Long cardId,
        @JsonProperty("time_spent") Integer timeSpent,
        @JsonProperty("for_date") LocalDate forDate
) {
}
