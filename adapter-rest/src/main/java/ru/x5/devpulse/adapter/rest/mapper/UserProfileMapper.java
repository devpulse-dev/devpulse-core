package ru.x5.devpulse.adapter.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import ru.x5.devpulse.adapter.rest.api.model.UserProfile;

/**
 * {@code domain.user.UnifiedUser} → {@link UserProfile}.
 *
 * <p>Все поля совпадают по имени. Конвертеры разворачивают {@code Email → String},
 * {@code KaitenUserId → Long}, {@code Integer gitlabId → Long}, {@code String avatarUrl → URI}.</p>
 */
@Mapper(componentModel = "spring",
        implementationName = "RestUserProfileMapperImpl",
        uses = DomainTypeConverters.class,
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface UserProfileMapper {

    UserProfile toDto(ru.x5.devpulse.domain.model.user.UnifiedUser u);
}
