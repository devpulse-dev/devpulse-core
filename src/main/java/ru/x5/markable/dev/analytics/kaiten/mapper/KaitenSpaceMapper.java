package ru.x5.markable.dev.analytics.kaiten.mapper;

import org.mapstruct.Mapper;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenSpaceDto;

import java.util.List;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenSpaceResponseDto;

@Mapper(componentModel = "spring")
public interface KaitenSpaceMapper {

    KaitenSpaceResponseDto toResponseDto(KaitenSpaceDto dto);

    List<KaitenSpaceResponseDto> toResponseDtoList(List<KaitenSpaceDto> dtos);
}
