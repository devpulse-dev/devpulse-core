package ru.x5.markable.dev.analytics.kaiten.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenUser;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenUserDto;

import java.util.List;

@Mapper(componentModel = "spring")
public interface KaitenUserMapper {

    @Mapping(target = "name", source = "fullName")
    @Mapping(target = "avatarUrl", source = "avatar")
    KaitenUser toEntity(KaitenUserDto dto);

    @Mapping(target = "fullName", source = "name")
    @Mapping(target = "avatar", source = "avatarUrl")
    KaitenUserDto toDto(KaitenUser entity);

    List<KaitenUser> toEntityList(List<KaitenUserDto> dtos);

    List<KaitenUserDto> toDtoList(List<KaitenUser> entities);

    @Mapping(target = "name", source = "fullName")
    @Mapping(target = "avatarUrl", source = "avatar")
    void updateEntity(@MappingTarget KaitenUser entity, KaitenUserDto dto);
}