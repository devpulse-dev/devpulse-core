package ru.x5.devpulse.adapter.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import ru.x5.devpulse.adapter.rest.api.model.WeeklyStats;

/**
 * {@code domain.stats.WeeklyStats} → {@link WeeklyStats}.
 *
 * <p>Все поля совпадают по имени; {@code authors} — список {@code AuthorSummary},
 * мапится через {@link AuthorSummaryMapper}.</p>
 */
@Mapper(componentModel = "spring",
        implementationName = "RestWeeklyStatsMapperImpl",
        uses = { DomainTypeConverters.class, AuthorSummaryMapper.class },
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface WeeklyStatsMapper {

    WeeklyStats toDto(ru.x5.devpulse.domain.model.stats.WeeklyStats w);
}
