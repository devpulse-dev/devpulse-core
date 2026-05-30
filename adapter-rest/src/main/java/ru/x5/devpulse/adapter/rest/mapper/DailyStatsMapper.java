package ru.x5.devpulse.adapter.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import ru.x5.devpulse.adapter.rest.api.model.DailyStats;

/**
 * {@code domain.stats.DailyAuthorStats} → {@link DailyStats}.
 *
 * <p>Domain называет поле {@code authorEmail}, контракт — {@code email}. Renaming.</p>
 */
@Mapper(componentModel = "spring",
        implementationName = "RestDailyStatsMapperImpl",
        uses = DomainTypeConverters.class,
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface DailyStatsMapper {

    @Mapping(target = "email", source = "authorEmail")
    DailyStats toDto(ru.x5.devpulse.domain.model.stats.DailyAuthorStats s);
}
