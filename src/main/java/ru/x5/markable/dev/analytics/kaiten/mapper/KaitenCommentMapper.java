package ru.x5.markable.dev.analytics.kaiten.mapper;

import org.mapstruct.Mapper;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenCardComment;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenCommentDto;

import java.util.List;

@Mapper(componentModel = "spring")
public interface KaitenCommentMapper {

    KaitenCardComment toEntity(KaitenCommentDto dto);

    KaitenCommentDto toDto(KaitenCardComment entity);

    List<KaitenCardComment> toEntityList(List<KaitenCommentDto> dtos);

    List<KaitenCommentDto> toDtoList(List<KaitenCardComment> entities);
}
