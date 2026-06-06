package ru.x5.devpulse.adapter.kaiten.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Карточка задачи, как её возвращает Kaiten API.
 *
 * <p>Берём только поля, которые нужны нашему domain'у (см. {@link
 * ru.x5.devpulse.domain.model.kaiten.KaitenCard KaitenCard}). Остальное
 * Jackson игнорирует.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KaitenCardDto(
        long id,
        String title,
        String description,
        /** Тип карточки: 70 = разработка, 8 = дефект, и т.д. См. KaitenCardType. */
        @JsonProperty("type_id") Integer typeId,
        /** Колонка, в которой карточка сейчас находится — отсюда статус. */
        @JsonProperty("column") KaitenColumnDto column,
        @JsonProperty("board") KaitenNamedRefDto board,
        @JsonProperty("space") KaitenNamedRefDto space,
        @JsonProperty("owner") KaitenUserDto owner,
        @JsonProperty("created") LocalDateTime createdAt,
        @JsonProperty("updated") LocalDateTime updatedAt,
        @JsonProperty("closed") LocalDateTime closedAt,
        boolean archived,
        List<KaitenMemberDto> members,
        /** Кастомные property карточки (динамические ключи). Срочность — {@code id_2561:[code]}. */
        @JsonProperty("properties") Map<String, Object> properties,
        /** Родительские карточки (для rollup разработки берём первую). */
        @JsonProperty("parents") List<KaitenParentDto> parents,
        /** Первый переход «в работу» — начало cycle-time. */
        @JsonProperty("first_moved_to_in_progress_at") LocalDateTime inProgressAt,
        /** Последний переход «готово» — конец cycle-time. */
        @JsonProperty("last_moved_to_done_at") LocalDateTime doneAt
) {

    /** Вложенная ссылка на board/space — только id и name. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KaitenNamedRefDto(long id, String name) {}

    /** Родительская карточка — берём id и title (url строим из id). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KaitenParentDto(long id, String title) {}

    /**
     * Колонка на доске: {@code title} — что показать (например «В уточнении»),
     * {@code type} — программная категория статуса (1=NEW, 2=IN_PROGRESS, 3=DONE).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KaitenColumnDto(long id, String title, Integer type) {}
}
