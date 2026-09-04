package ru.x5.devpulse.adapter.kaiten.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.Map;

/**
 * Запись списания времени, как её возвращает {@code GET /time-logs} Kaiten.
 *
 * <p>Ответ API «толстый»: в каждом логе едут вложенные {@code card} и {@code user} целиком
 * (100 логов ≈ несколько МБ). Берём только нужное: скаляры лога + узкий срез карточки
 * (заголовок, тип, кастомные property для флага AI-Agent). Остальное Jackson игнорирует
 * ({@link JsonIgnoreProperties}) — в память лишнее не поднимается.</p>
 *
 * @param timeSpent списанные минуты
 * @param forDate   день, на который списано время (не дата создания записи)
 * @param card      узкий срез карточки: на что списано время
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KaitenTimeLogDto(
        long id,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("card_id") Long cardId,
        @JsonProperty("time_spent") Integer timeSpent,
        @JsonProperty("for_date") LocalDate forDate,
        @JsonProperty("card") TimeLogCardDto card
) {

    /** Карточка внутри лога — только то, что нужно таймшиту. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TimeLogCardDto(
            long id,
            String title,
            @JsonProperty("type_id") Integer typeId,
            /** Кастомные property карточки: отсюда берём флаг «AI-Agent» ({@code id_6064}). */
            @JsonProperty("properties") Map<String, Object> properties
    ) {}
}
