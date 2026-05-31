package ru.x5.devpulse.adapter.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import ru.x5.devpulse.adapter.rest.api.model.PeriodSummary;

/**
 * {@code domain.stats.PeriodSummary} → {@link PeriodSummary}.
 *
 * <p>Уплощает {@code period: Period} в плоские {@code from}/{@code to}.</p>
 */
@Mapper(componentModel = "spring",
        implementationName = "RestPeriodSummaryMapperImpl",
        uses = { DomainTypeConverters.class, AuthorSummaryMapper.class },
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface PeriodSummaryMapper {

    @Mapping(target = "from", source = "period.from")
    @Mapping(target = "to", source = "period.to")
    PeriodSummary toDto(ru.x5.devpulse.domain.model.stats.PeriodSummary s);
}
