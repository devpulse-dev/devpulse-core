package ru.x5.devpulse.adapter.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import ru.x5.devpulse.adapter.rest.api.model.MergedMrByAuthor;
import ru.x5.devpulse.adapter.rest.api.model.MergedMrByRepo;
import ru.x5.devpulse.adapter.rest.api.model.MergedMrStats;
import ru.x5.devpulse.domain.model.review.AuthorMergedMrCount;
import ru.x5.devpulse.domain.model.review.RepoMergedMrCount;
import ru.x5.devpulse.domain.model.review.TeamMergedMrStats;

/**
 * {@code domain.review.TeamMergedMrStats} → {@link MergedMrStats}.
 *
 * <p>Уплощает {@code period} в {@code from}/{@code to}; {@code AuthorMergedMrCount} →
 * {@link MergedMrByAuthor} (Email→String через {@link DomainTypeConverters}; displayName/avatarUrl —
 * строки как есть).</p>
 */
@Mapper(componentModel = "spring",
        implementationName = "RestMergedMrStatsMapperImpl",
        uses = DomainTypeConverters.class,
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface MergedMrStatsMapper {

    @Mapping(target = "from", source = "period.from")
    @Mapping(target = "to", source = "period.to")
    MergedMrStats toDto(TeamMergedMrStats stats);

    MergedMrByAuthor toAuthor(AuthorMergedMrCount author);

    MergedMrByRepo toRepo(RepoMergedMrCount repo);
}
