package ru.x5.devpulse.adapter.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import ru.x5.devpulse.adapter.rest.api.model.Timesheet;
import ru.x5.devpulse.adapter.rest.api.model.TimesheetDay;

/**
 * {@code domain.performance.Timesheet} → {@link Timesheet}.
 *
 * <p>Уплощает {@code period} в {@code from}/{@code to}; {@code loggedDays} — производный геттер
 * домена (не record-компонент), поэтому берётся выражением. Email → String через
 * {@link DomainTypeConverters}.</p>
 */
@Mapper(componentModel = "spring",
        implementationName = "RestTimesheetMapperImpl",
        uses = DomainTypeConverters.class,
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface TimesheetMapper {

    @Mapping(target = "from", source = "period.from")
    @Mapping(target = "to", source = "period.to")
    @Mapping(target = "loggedDays", expression = "java(ts.loggedDays())")
    Timesheet toDto(ru.x5.devpulse.domain.model.performance.Timesheet ts);

    TimesheetDay toDay(ru.x5.devpulse.domain.model.performance.TimesheetDay day);
}
