package ru.x5.markable.dev.analytics.kaiten.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenUser;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenUserDto;

import java.util.List;

/**
 * MapStruct маппер для преобразования между сущностью {@link KaitenUser} и DTO {@link KaitenUserDto}.
 * 
 * <p>Использует Spring component model для интеграции с контекстом Spring.
 * Предоставляет методы для преобразования отдельных объектов и списков, а также метод для обновления
 * существующей сущности данными из DTO.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see KaitenUser
 * @see KaitenUserDto
 */
@Mapper(componentModel = "spring")
public interface KaitenUserMapper {

    /**
     * Преобразует DTO пользователя Kaiten в сущность для сохранения в базе данных.
     * 
     * @param dto DTO пользователя Kaiten
     * @return сущность пользователя Kaiten
     */
    @Mapping(target = "name", source = "fullName")
    @Mapping(target = "avatarUrl", source = "avatar")
    KaitenUser toEntity(KaitenUserDto dto);

    /**
     * Преобразует сущность пользователя Kaiten в DTO.
     * 
     * @param entity сущность пользователя Kaiten
     * @return DTO пользователя Kaiten
     */
    @Mapping(target = "fullName", source = "name")
    @Mapping(target = "avatar", source = "avatarUrl")
    KaitenUserDto toDto(KaitenUser entity);

    /**
     * Преобразует список DTO пользователей Kaiten в список сущностей.
     * 
     * @param dtos список DTO пользователей Kaiten
     * @return список сущностей пользователей Kaiten
     */
    List<KaitenUser> toEntityList(List<KaitenUserDto> dtos);

    /**
     * Преобразует список сущностей пользователей Kaiten в список DTO.
     * 
     * @param entities список сущностей пользователей Kaiten
     * @return список DTO пользователей Kaiten
     */
    List<KaitenUserDto> toDtoList(List<KaitenUser> entities);

    /**
     * Обновляет существующую сущность пользователя Kaiten данными из DTO.
     * 
     * <p>Использует аннотацию @MappingTarget для указания, что первый параметр является целевым объектом,
     * который будет обновлен данными из второго параметра.</p>
     * 
     * @param entity сущность пользователя Kaiten для обновления
     * @param dto DTO пользователя Kaiten с новыми данными
     */
    @Mapping(target = "name", source = "fullName")
    @Mapping(target = "avatarUrl", source = "avatar")
    void updateEntity(@MappingTarget KaitenUser entity, KaitenUserDto dto);
}