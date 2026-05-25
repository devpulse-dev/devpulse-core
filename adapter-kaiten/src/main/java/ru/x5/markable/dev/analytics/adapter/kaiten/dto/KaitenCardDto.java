package ru.x5.markable.dev.analytics.adapter.kaiten.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Карточка задачи, как её возвращает Kaiten API.
 *
 * <p>Берём только поля, которые нужны нашему domain'у (см. {@link
 * ru.x5.markable.dev.analytics.domain.model.kaiten.KaitenCard KaitenCard}). Остальное
 * Jackson игнорирует.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KaitenCardDto(
        long id,
        String title,
        String description,
        String state,
        @JsonProperty("column") KaitenNamedRefDto column,
        @JsonProperty("board") KaitenNamedRefDto board,
        @JsonProperty("space") KaitenNamedRefDto space,
        @JsonProperty("owner") KaitenUserDto owner,
        @JsonProperty("created") LocalDateTime createdAt,
        @JsonProperty("updated") LocalDateTime updatedAt,
        @JsonProperty("closed") LocalDateTime closedAt,
        boolean archived,
        List<KaitenMemberDto> members
) {

    /** Вложенная ссылка на board/space/column — только id и name. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KaitenNamedRefDto(long id, String name) {}
}
