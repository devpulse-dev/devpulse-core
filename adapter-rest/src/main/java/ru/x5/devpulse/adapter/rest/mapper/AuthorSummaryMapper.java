package ru.x5.devpulse.adapter.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import ru.x5.devpulse.adapter.rest.api.model.AuthorSummary;

/**
 * {@code domain.stats.AuthorSummary} → {@link AuthorSummary}.
 *
 * <p>{@code nonMergeCommits} — computed accessor record'а ({@code commits − mergeCommits}),
 * не record-component → MapStruct сам бы не нашёл; ставим явно через expression.</p>
 */
@Mapper(componentModel = "spring",
        implementationName = "RestAuthorSummaryMapperImpl",
        uses = { DomainTypeConverters.class, ActivityScoreMapper.class },
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface AuthorSummaryMapper {

    @Mapping(target = "nonMergeCommits", expression = "java(s.nonMergeCommits())")
    AuthorSummary toDto(ru.x5.devpulse.domain.model.stats.AuthorSummary s);
}
