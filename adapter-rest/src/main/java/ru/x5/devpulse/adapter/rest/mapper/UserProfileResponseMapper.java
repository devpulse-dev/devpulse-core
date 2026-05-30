package ru.x5.devpulse.adapter.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import ru.x5.devpulse.adapter.rest.api.model.UserProfileResponse;

/**
 * {@code domain.stats.UserProfile} → {@link UserProfileResponse}.
 *
 * <p>Композитный mapper: каждое поле делегирует на свой sub-mapper:
 * <ul>
 *   <li>{@code user}: {@link UserProfileMapper}</li>
 *   <li>{@code summary}: {@link UserStatsSummaryMapper} (downcast {@code AuthorSummary})</li>
 *   <li>{@code commits}: {@link CommitMapper} per-element</li>
 *   <li>{@code cards}: {@link KaitenCardMapper} per-element</li>
 * </ul></p>
 */
@Mapper(componentModel = "spring",
        implementationName = "RestUserProfileResponseMapperImpl",
        uses = {
                UserProfileMapper.class,
                UserStatsSummaryMapper.class,
                CommitMapper.class,
                KaitenCardMapper.class
        },
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface UserProfileResponseMapper {

    UserProfileResponse toDto(ru.x5.devpulse.domain.model.stats.UserProfile p);
}
