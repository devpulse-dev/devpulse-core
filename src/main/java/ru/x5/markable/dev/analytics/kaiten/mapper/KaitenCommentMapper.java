package ru.x5.markable.dev.analytics.kaiten.mapper;

import org.mapstruct.Mapper;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenCardComment;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenCommentDto;

import java.util.List;

/**
 * MapStruct маппер для преобразования между DTO {@link KaitenCommentDto} и сущностью {@link KaitenCardComment}.
 * 
 * <p>Использует Spring component model для интеграции с контекстом Spring.
 * Предоставляет методы для преобразования отдельных объектов и списков.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see KaitenCardComment
 * @see KaitenCommentDto
 */
@Mapper(componentModel = "spring")
public interface KaitenCommentMapper {

    /**
     * Преобразует DTO комментария Kaiten в сущность для сохранения в базе данных.
     * 
     * @param dto DTO комментария Kaiten
     * @return сущность комментария Kaiten
     */
    KaitenCardComment toEntity(KaitenCommentDto dto);

    /**
     * Преобразует сущность комментария Kaiten в DTO.
     * 
     * @param entity сущность комментария Kaiten
     * @return DTO комментария Kaiten
     */
    KaitenCommentDto toDto(KaitenCardComment entity);

    /**
     * Преобразует список DTO комментариев Kaiten в список сущностей.
     * 
     * @param dtos список DTO комментариев Kaiten
     * @return список сущностей комментариев Kaiten
     */
    List<KaitenCardComment> toEntityList(List<KaitenCommentDto> dtos);

    /**
     * Преобразует список сущностей комментариев Kaiten в список DTO.
     * 
     * @param entities список сущностей комментариев Kaiten
     * @return список DTO комментариев Kaiten
     */
    List<KaitenCommentDto> toDtoList(List<KaitenCardComment> entities);
}
