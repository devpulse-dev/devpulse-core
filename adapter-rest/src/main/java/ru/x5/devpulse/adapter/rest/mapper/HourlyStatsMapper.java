package ru.x5.devpulse.adapter.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import ru.x5.devpulse.adapter.rest.api.model.HourlyCell;
import ru.x5.devpulse.adapter.rest.api.model.HourlyStats;

/**
 * {@code domain.stats.HourlyStats} → {@link HourlyStats}.
 *
 * <p>Уплощает {@code period: Period} в плоские {@code from}/{@code to}; вложенные
 * {@code HourlyBucket} мапятся в {@link HourlyCell} (имена полей совпадают,
 * int/long → Integer/Long автобоксятся).</p>
 */
@Mapper(componentModel = "spring",
        implementationName = "RestHourlyStatsMapperImpl",
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface HourlyStatsMapper {

    @Mapping(target = "from", source = "period.from")
    @Mapping(target = "to", source = "period.to")
    HourlyStats toDto(ru.x5.devpulse.domain.model.stats.HourlyStats s);

    HourlyCell toCell(ru.x5.devpulse.domain.model.stats.HourlyBucket b);
}
