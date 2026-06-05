package ru.x5.devpulse.adapter.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import ru.x5.devpulse.adapter.rest.api.model.MetricDelta;
import ru.x5.devpulse.adapter.rest.api.model.PerformanceHighlight;
import ru.x5.devpulse.adapter.rest.api.model.PerformanceMetrics;
import ru.x5.devpulse.adapter.rest.api.model.PerformanceReview;
import ru.x5.devpulse.adapter.rest.api.model.Period;
import ru.x5.devpulse.adapter.rest.api.model.TaskStatusCounts;
import ru.x5.devpulse.adapter.rest.api.model.TaskTypeBreakdown;

/**
 * {@code domain.performance.PerformanceReview} → {@link PerformanceReview}.
 *
 * <p>{@code subject} ({@code UnifiedUser}) делегируется на {@link UserProfileMapper};
 * {@code url}/{@code avatarUrl} (String → URI), {@code Email}/{@code KaitenUserId} разворачиваются
 * через {@link DomainTypeConverters}; enum {@code Kind → KindEnum} и совпадающие по имени поля
 * MapStruct мапит автоматически.</p>
 */
@Mapper(componentModel = "spring",
        implementationName = "RestPerformanceReviewMapperImpl",
        uses = {UserProfileMapper.class, DomainTypeConverters.class},
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface PerformanceReviewMapper {

    PerformanceReview toDto(ru.x5.devpulse.domain.model.performance.PerformanceReview r);

    Period toPeriod(ru.x5.devpulse.domain.common.Period p);

    MetricDelta toDelta(ru.x5.devpulse.domain.model.performance.MetricDelta d);

    PerformanceMetrics toMetrics(ru.x5.devpulse.domain.model.performance.PerformanceMetrics m);

    TaskTypeBreakdown toBreakdown(ru.x5.devpulse.domain.model.performance.TaskTypeBreakdown b);

    TaskStatusCounts toCounts(ru.x5.devpulse.domain.model.performance.TaskStatusCounts c);

    PerformanceHighlight toHighlight(ru.x5.devpulse.domain.model.performance.PerformanceHighlight h);
}
