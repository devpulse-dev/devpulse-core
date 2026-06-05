package ru.x5.devpulse.adapter.rest.mapper;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import ru.x5.devpulse.adapter.rest.api.model.ReviewAuthor;
import ru.x5.devpulse.adapter.rest.api.model.ReviewStats;

/**
 * {@code domain.review.ReviewStats} → {@link ReviewStats}.
 *
 * <p>Уплощает {@code period} в {@code from}/{@code to}; {@code ReviewAuthorStats} →
 * {@link ReviewAuthor} (имена совпадают; Email→String, String avatarUrl→URI через
 * {@link DomainTypeConverters}). {@code avgTimeToMergeHours} округляется до 1 знака.</p>
 */
@Mapper(componentModel = "spring",
        implementationName = "RestReviewStatsMapperImpl",
        uses = DomainTypeConverters.class,
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface ReviewStatsMapper {

    @Mapping(target = "from", source = "period.from")
    @Mapping(target = "to", source = "period.to")
    ReviewStats toDto(ru.x5.devpulse.domain.model.review.ReviewStats s);

    @Mapping(target = "isLead", source = "lead")
    ReviewAuthor toAuthor(ru.x5.devpulse.domain.model.review.ReviewAuthorStats a);

    @AfterMapping
    default void roundTtm(@MappingTarget ReviewAuthor dto) {
        if (dto.getAvgTimeToMergeHours() != null) {
            dto.setAvgTimeToMergeHours(Math.round(dto.getAvgTimeToMergeHours() * 10.0) / 10.0);
        }
    }
}
