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

@Mapper(componentModel = "spring")
public interface KaitenCardMapper {

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
    @Mapping(target = "members", ignore = true)  // члены карточки сохраняются отдельно
    KaitenCard toEntity(KaitenCardDto dto);

    KaitenCardResponseDto toResponseDto(KaitenCard entity);

    List<KaitenCardResponseDto> toResponseDtoList(List<KaitenCard> entities);

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

    @Named("mapTagsToString")
    default String mapTagsToString(List<KaitenTagDto> tags) {
        if (tags == null || tags.isEmpty()) return null;
        return tags.stream()
                .map(KaitenTagDto::getName)
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
    }

    @Named("buildCardUrl")
    default String buildCardUrl(Long id) {
        if (id == null) return null;
        return "https://kaiten.x5.ru/" + id;
    }
}