package ru.x5.devpulse.adapter.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import ru.x5.devpulse.adapter.rest.api.model.UserStatsSummary;

/**
 * {@code domain.stats.AuthorSummary} → {@link UserStatsSummary} (даункаст).
 *
 * <p>Бэк всегда считает полную {@link ru.x5.devpulse.domain.model.stats.AuthorSummary
 * AuthorSummary}, но эндпоинт {@code /users/.../profile} не нуждается в полях enrichment'а
 * ({@code displayName}, {@code avatarUrl}, {@code nonMergeCommits}, {@code activity}).
 * Здесь проектируем только нужные.</p>
 */
@Mapper(componentModel = "spring",
        implementationName = "RestUserStatsSummaryMapperImpl",
        uses = DomainTypeConverters.class,
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface UserStatsSummaryMapper {

    UserStatsSummary toDto(ru.x5.devpulse.domain.model.stats.AuthorSummary s);
}
