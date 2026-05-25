package ru.x5.markable.dev.analytics.adapter.persistence.kaiten;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.x5.markable.dev.analytics.domain.model.kaiten.KaitenUser;
import ru.x5.markable.dev.analytics.domain.model.user.Email;
import ru.x5.markable.dev.analytics.domain.model.user.KaitenUserId;

@Mapper(componentModel = "spring")
interface KaitenUserEntityMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = "toId")
    @Mapping(target = "email", source = "email", qualifiedByName = "toEmail")
    @Mapping(target = "fullName", source = "name")
    KaitenUser toDomain(KaitenUserEntity entity);

    @Mapping(target = "id", source = "id.value")
    @Mapping(target = "email", source = "email.value")
    @Mapping(target = "name", source = "fullName")
    KaitenUserEntity toEntity(KaitenUser domain);

    @Named("toId")
    default KaitenUserId toId(Long v) {
        return v == null ? null : new KaitenUserId(v);
    }

    @Named("toEmail")
    default Email toEmail(String v) {
        return v == null ? null : new Email(v);
    }
}
