package ru.x5.devpulse.adapter.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import ru.x5.devpulse.adapter.rest.api.model.Team;

/**
 * {@code domain.user.Team} → {@link Team}.
 *
 * <p>{@code lead}/{@code members} ({@code UnifiedUser}) делегируются на {@link UserProfileMapper}.</p>
 */
@Mapper(componentModel = "spring",
        implementationName = "RestTeamMapperImpl",
        uses = UserProfileMapper.class,
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface TeamMapper {

    Team toDto(ru.x5.devpulse.domain.model.user.Team t);
}
