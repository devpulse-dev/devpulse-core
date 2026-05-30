package ru.x5.devpulse.adapter.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import ru.x5.devpulse.adapter.rest.api.model.DashboardResponse;

/**
 * {@code domain.stats.Dashboard} → {@link DashboardResponse}.
 *
 * <p>Уплощает вложенные {@code Period} и {@code Page} в плоскую структуру контракта.
 * {@code totalPages} и {@code hasNext} — computed-методы {@code Page}, не record-components
 * → ставим через expression.</p>
 */
@Mapper(componentModel = "spring",
        implementationName = "RestDashboardMapperImpl",
        uses = AuthorSummaryMapper.class,
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface DashboardMapper {

    @Mapping(target = "from", source = "period.from")
    @Mapping(target = "to", source = "period.to")
    @Mapping(target = "page", source = "authors.page")
    @Mapping(target = "size", source = "authors.size")
    @Mapping(target = "totalElements", source = "authors.totalElements")
    @Mapping(target = "totalPages", expression = "java(d.authors().totalPages())")
    @Mapping(target = "hasNext", expression = "java(d.authors().hasNext())")
    @Mapping(target = "items", source = "authors.items")
    DashboardResponse toDto(ru.x5.devpulse.domain.model.stats.Dashboard d);
}
