package ru.x5.devpulse.adapter.rest.mapper;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import ru.x5.devpulse.adapter.rest.api.model.ActivityScore;

/**
 * {@code domain.stats.ActivityScore} → {@link ActivityScore}.
 *
 * <p>Все поля совпадают; {@code ActivityCategory} → {@code CategoryEnum} автоматически
 * по имени значения. {@link AfterMapping} округляет double-значения до 3 знаков:
 * фронту приятнее видеть {@code 0.686} чем {@code 0.6857142857142857}.</p>
 */
@Mapper(componentModel = "spring",
        implementationName = "RestActivityScoreMapperImpl",
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface ActivityScoreMapper {

    ActivityScore toDto(ru.x5.devpulse.domain.model.stats.ActivityScore s);

    @AfterMapping
    default void roundFactors(@MappingTarget ActivityScore dto) {
        dto.setScore(round3(dto.getScore()));
        dto.setVolumeFactor(round3(dto.getVolumeFactor()));
        dto.setQualityFactor(round3(dto.getQualityFactor()));
        dto.setAvgLinesPerCommit(round3(dto.getAvgLinesPerCommit()));
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
