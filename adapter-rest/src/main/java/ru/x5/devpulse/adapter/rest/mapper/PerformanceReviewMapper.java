package ru.x5.devpulse.adapter.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import ru.x5.devpulse.adapter.rest.api.model.CycleTime;
import ru.x5.devpulse.adapter.rest.api.model.CycleTimeBreakdown;
import ru.x5.devpulse.adapter.rest.api.model.DefectsSummary;
import ru.x5.devpulse.adapter.rest.api.model.DeliveredFeature;
import ru.x5.devpulse.adapter.rest.api.model.DevelopmentRollup;
import ru.x5.devpulse.adapter.rest.api.model.FirefightingItem;
import ru.x5.devpulse.adapter.rest.api.model.KaitenInsights;
import ru.x5.devpulse.adapter.rest.api.model.MetricDelta;
import ru.x5.devpulse.adapter.rest.api.model.NotableResults;
import ru.x5.devpulse.adapter.rest.api.model.PerformanceMetrics;
import ru.x5.devpulse.adapter.rest.api.model.PerformanceReview;
import ru.x5.devpulse.adapter.rest.api.model.Period;
import ru.x5.devpulse.adapter.rest.api.model.RootTask;
import ru.x5.devpulse.adapter.rest.api.model.TaskStatusCounts;
import ru.x5.devpulse.adapter.rest.api.model.TaskTypeBreakdown;
import ru.x5.devpulse.adapter.rest.api.model.UrgencyCounts;
import ru.x5.devpulse.adapter.rest.api.model.UseCaseRef;
import ru.x5.devpulse.adapter.rest.api.model.WorkBalance;

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

    // ── Заметные результаты (тушение пожаров / доставленные доработки) ──

    NotableResults toNotable(ru.x5.devpulse.domain.model.performance.NotableResults n);

    FirefightingItem toFirefighting(ru.x5.devpulse.domain.model.performance.FirefightingItem f);

    DeliveredFeature toDeliveredFeature(ru.x5.devpulse.domain.model.performance.DeliveredFeature d);

    // ── Kaiten insights (дефекты по срочности / rollup разработки / cycle-time / баланс) ──

    KaitenInsights toInsights(ru.x5.devpulse.domain.model.performance.KaitenInsights k);

    DefectsSummary toDefects(ru.x5.devpulse.domain.model.performance.DefectsSummary d);

    UrgencyCounts toUrgency(ru.x5.devpulse.domain.model.performance.UrgencyCounts u);

    DevelopmentRollup toDevelopment(ru.x5.devpulse.domain.model.performance.DevelopmentRollup d);

    RootTask toRoot(ru.x5.devpulse.domain.model.performance.RootTask r);

    UseCaseRef toUseCase(ru.x5.devpulse.domain.model.performance.UseCaseRef u);

    CycleTimeBreakdown toCycleTimeBreakdown(ru.x5.devpulse.domain.model.performance.CycleTimeBreakdown c);

    CycleTime toCycleTime(ru.x5.devpulse.domain.model.performance.CycleTime c);

    WorkBalance toBalance(ru.x5.devpulse.domain.model.performance.WorkBalance w);
}
