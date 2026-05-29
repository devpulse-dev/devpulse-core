package ru.x5.devpulse.adapter.persistence.user;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.KaitenUserId;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

@Mapper(componentModel = "spring")
interface UnifiedUserEntityMapper {

    @Mapping(target = "email", source = "email", qualifiedByName = "toEmail")
    @Mapping(target = "kaitenId", source = "kaitenId", qualifiedByName = "toKaitenUserId")
    UnifiedUser toDomain(UnifiedUserEntity entity);

    @Named("toEmail")
    default Email toEmail(String value) {
        return value == null ? null : new Email(value);
    }

    @Named("toKaitenUserId")
    default KaitenUserId toKaitenUserId(Long value) {
        return value == null ? null : new KaitenUserId(value);
    }
}
