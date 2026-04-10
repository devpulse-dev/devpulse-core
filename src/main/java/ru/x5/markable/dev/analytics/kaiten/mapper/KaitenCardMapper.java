package ru.x5.markable.dev.analytics.kaiten.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenCard;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenCardDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.card.KaitenTagDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenCardResponseDto;

import java.util.List;
import java.util.Map;

/**
 * MapStruct маппер для преобразования между DTO {@link KaitenCardDto} и сущностью {@link KaitenCard}.
 * 
 * <p>Использует Spring component model для интеграции с контекстом Spring.
 * Предоставляет методы для преобразования с использованием кастомных методов для маппинга
 * статуса, приоритета, тегов и построения URL карточки.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see KaitenCard
 * @see KaitenCardDto
 * @see KaitenCardResponseDto
 */
@Mapper(componentModel = "spring")
public interface KaitenCardMapper {

    /**
     * Преобразует DTO карточки Kaiten в сущность для сохранения в базе данных.
     * 
     * <p>Выполняет сложное маппинг с использованием кастомных методов для преобразования
     * статуса, приоритета, тегов и построения URL карточки.</p>
     * 
     * @param dto DTO карточки Kaiten
     * @return сущность карточки Kaiten
     */
    @Mapping(target = "status", source = "state", qualifiedByName = "mapStateToStatus")
    @Mapping(target = "priority", source = "properties", qualifiedByName = "extractPriority")
    @Mapping(target = "boardId", source = "board.id")
    @Mapping(target = "boardName", source = "board.title")
    @Mapping(target = "ownerId", source = "owner.id")
    @Mapping(target = "ownerName", source = "owner.fullName")
    @Mapping(target = "typeId", source = "type.id")
    @Mapping(target = "typeName", source = "type.name")
    @Mapping(target = "columnId", source = "column.id")
    @Mapping(target = "columnName", source = "column.title")
    @Mapping(target = "laneId", source = "lane.id")
    @Mapping(target = "laneName", source = "lane.title")
    @Mapping(target = "tags", source = "tags", qualifiedByName = "mapTagsToString")
    @Mapping(target = "customFields", source = "properties")
    @Mapping(target = "url", source = "id", qualifiedByName = "buildCardUrl")
    KaitenCard toEntity(KaitenCardDto dto);

    /**
     * Преобразует сущность карточки Kaiten в DTO ответа.
     * 
     * @param entity сущность карточки Kaiten
     * @return DTO ответа с данными карточки
     */
    KaitenCardResponseDto toResponseDto(KaitenCard entity);

    /**
     * Преобразует список сущностей карточек Kaiten в список DTO ответов.
     * 
     * @param entities список сущностей карточек Kaiten
     * @return список DTO ответов с данными карточек
     */
    List<KaitenCardResponseDto> toResponseDtoList(List<KaitenCard> entities);

    /**
     * Преобразует числовой статус карточки в строковое представление.
     * 
     * <p>Использует следующее соответствие:</p>
     * <ul>
     *   <li>1 - "Очередь"</li>
     *   <li>2 - "В работе"</li>
     *   <li>3 - "Готово"</li>
     *   <li>другое - "Неизвестно"</li>
     * </ul>
     * 
     * @param state числовой статус карточки
     * @return строковое представление статуса
     */
    @Named("mapStateToStatus")
    default String mapStateToStatus(Integer state) {
        if (state == null) return "Неизвестно";
        return switch (state) {
            case 1 -> "Очередь";
            case 2 -> "В работе";
            case 3 -> "Готово";
            default -> "Неизвестно";
        };
    }

    /**
     * Извлекает приоритет из свойств карточки.
     * 
     * <p>Приоритет может приходить как массив или как число. Использует следующее соответствие:</p>
     * <ul>
     *   <li>4526 - "Низкий"</li>
     *   <li>4525 - "Средний"</li>
     *   <li>4524 - "Высокий"</li>
     *   <li>4523 - "Крит"</li>
     * </ul>
     * 
     * @param properties карта свойств карточки
     * @return строковое представление приоритета, или null если приоритет не найден
     */
    @Named("extractPriority")
    default String extractPriority(Map<String, Object> properties) {
        if (properties == null) return null;

        Object priorityValue = properties.get("id_2561");
        if (priorityValue == null) return null;

        // Приоритет может приходить как массив или как число
        Long priorityId = null;
        if (priorityValue instanceof List) {
            List<?> list = (List<?>) priorityValue;
            if (!list.isEmpty() && list.get(0) instanceof Number) {
                priorityId = ((Number) list.get(0)).longValue();
            }
        } else if (priorityValue instanceof Number) {
            priorityId = ((Number) priorityValue).longValue();
        }

        if (priorityId == null) return null;

        return switch (priorityId.intValue()) {
            case 4526 -> "Низкий";
            case 4525 -> "Средний";
            case 4524 -> "Высокий";
            case 4523 -> "Крит";
            default -> null;
        };
    }

    /**
     * Преобразует список тегов в строку, разделенную запятыми.
     * 
     * @param tags список тегов карточки
     * @return строка с тегами, разделенными запятыми, или null если список пуст или равен null
     */
    @Named("mapTagsToString")
    default String mapTagsToString(List<KaitenTagDto> tags) {
        if (tags == null || tags.isEmpty()) return null;
        return tags.stream()
                .map(KaitenTagDto::getName)
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
    }

    /**
     * Строит URL карточки Kaiten по её идентификатору.
     * 
     * @param id идентификатор карточки
     * @return URL карточки в формате "https://kaiten.x5.ru/{id}", или null если id равен null
     */
    @Named("buildCardUrl")
    default String buildCardUrl(Long id) {
        if (id == null) return null;
        return "https://kaiten.x5.ru/" + id;
    }
}