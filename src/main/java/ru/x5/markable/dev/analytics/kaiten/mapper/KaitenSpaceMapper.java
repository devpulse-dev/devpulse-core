package ru.x5.markable.dev.analytics.kaiten.mapper;

import org.mapstruct.Mapper;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenSpaceDto;

import java.util.List;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenSpaceResponseDto;

/**
 * MapStruct маппер для преобразования между DTO {@link KaitenSpaceDto} и DTO ответа {@link KaitenSpaceResponseDto}.
 * 
 * <p>Использует Spring component model для интеграции с контекстом Spring.
 * Предоставляет методы для преобразования отдельных объектов и списков пространств Kaiten.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see KaitenSpaceDto
 * @see KaitenSpaceResponseDto
 */
@Mapper(componentModel = "spring")
public interface KaitenSpaceMapper {

    /**
     * Преобразует DTO пространства Kaiten в DTO ответа.
     * 
     * @param dto DTO пространства Kaiten
     * @return DTO ответа с данными пространства
     */
    KaitenSpaceResponseDto toResponseDto(KaitenSpaceDto dto);

    /**
     * Преобразует список DTO пространств Kaiten в список DTO ответов.
     * 
     * @param dtos список DTO пространств Kaiten
     * @return список DTO ответов с данными пространств
     */
    List<KaitenSpaceResponseDto> toResponseDtoList(List<KaitenSpaceDto> dtos);
}
