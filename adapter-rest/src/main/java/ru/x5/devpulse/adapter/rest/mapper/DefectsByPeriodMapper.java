package ru.x5.devpulse.adapter.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import ru.x5.devpulse.adapter.rest.api.model.DefectItem;
import ru.x5.devpulse.adapter.rest.api.model.DefectMember;
import ru.x5.devpulse.adapter.rest.api.model.DefectsByPeriodResponse;
import ru.x5.devpulse.adapter.rest.api.model.PeriodDefects;
import ru.x5.devpulse.adapter.rest.api.model.PriorityCounts;
import ru.x5.devpulse.domain.model.performance.DefectDetail;
import ru.x5.devpulse.domain.model.performance.PeriodDefectCounts;
import ru.x5.devpulse.domain.model.performance.TeamDefectsReport;
import ru.x5.devpulse.domain.model.performance.UrgencyCounts;

/**
 * {@code domain.performance.TeamDefectsReport} → {@link DefectsByPeriodResponse}.
 *
 * <p>Уплощает {@code Period} в {@code from}/{@code to}; {@code total} берётся из
 * {@link UrgencyCounts#total()}; {@code UrgencyCounts} → {@link PriorityCounts} (имена полей
 * совпадают). Детализация: {@link DefectDetail} → {@link DefectItem} (KaitenCardId/Email →
 * скаляры через {@link DomainTypeConverters}). Доменный {@code DefectMember} тёзка DTO —
 * поэтому полностью квалифицирован.</p>
 */
@Mapper(componentModel = "spring",
        implementationName = "RestDefectsByPeriodMapperImpl",
        uses = DomainTypeConverters.class,
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface DefectsByPeriodMapper {

    DefectsByPeriodResponse toDto(TeamDefectsReport report);

    @Mapping(target = "from", source = "period.from")
    @Mapping(target = "to", source = "period.to")
    @Mapping(target = "total", expression = "java(pc.counts().total())")
    @Mapping(target = "byPriority", source = "counts")
    @Mapping(target = "aiAgentCount", source = "aiAgentCount")
    PeriodDefects toDto(PeriodDefectCounts pc);

    PriorityCounts toPriority(UrgencyCounts counts);

    DefectItem toItem(DefectDetail detail);

    DefectMember toMember(ru.x5.devpulse.domain.model.performance.DefectMember member);
}
