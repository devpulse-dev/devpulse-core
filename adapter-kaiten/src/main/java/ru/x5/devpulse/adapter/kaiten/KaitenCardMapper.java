package ru.x5.devpulse.adapter.kaiten;

import java.util.List;
import java.util.Optional;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.x5.devpulse.adapter.kaiten.dto.KaitenCardDto;
import ru.x5.devpulse.adapter.kaiten.dto.KaitenMemberDto;
import ru.x5.devpulse.adapter.kaiten.dto.KaitenUserDto;
import ru.x5.devpulse.domain.model.kaiten.KaitenCard;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardId;
import ru.x5.devpulse.domain.model.kaiten.KaitenUser;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.KaitenUserId;

/**
 * Маппинг HTTP DTO ↔ domain.
 *
 * <p>Поля API не совпадают 1-в-1 с domain: вложенные {@code board.name} / {@code space.name},
 * {@code state} вместо {@code status}, и т.д. — раскрываем явными mappings.</p>
 */
@Mapper(componentModel = "spring")
interface KaitenCardMapper {

    @Mapping(target = "id", expression = "java(new KaitenCardId(dto.id()))")
    @Mapping(target = "typeId", source = "typeId")
    @Mapping(target = "columnType", source = "column.type")
    @Mapping(target = "columnTitle", source = "column.title")
    @Mapping(target = "boardName", source = "board.name")
    @Mapping(target = "spaceName", source = "space.name")
    @Mapping(target = "ownerId", source = "owner", qualifiedByName = "ownerToKaitenUserId")
    @Mapping(target = "ownerName", source = "owner.fullName")
    @Mapping(target = "url", ignore = true)              // дополняется адаптером
    @Mapping(target = "memberIds", source = "members", qualifiedByName = "membersToIds")
    KaitenCard toDomain(KaitenCardDto dto);

    @Mapping(target = "id", expression = "java(new KaitenUserId(dto.id()))")
    @Mapping(target = "email", source = "email", qualifiedByName = "stringToEmail")
    @Mapping(target = "fullName", source = "fullName")
    @Mapping(target = "lastSyncedAt", ignore = true)
    KaitenUser toDomain(KaitenUserDto dto);

    @Named("ownerToKaitenUserId")
    default KaitenUserId ownerToKaitenUserId(KaitenUserDto owner) {
        return owner == null ? null : new KaitenUserId(owner.id());
    }

    @Named("membersToIds")
    default List<KaitenUserId> membersToIds(List<KaitenMemberDto> members) {
        return members == null
                ? List.of()
                : members.stream().map(m -> new KaitenUserId(m.id())).toList();
    }

    @Named("stringToEmail")
    default Email stringToEmail(String value) {
        // У сервисных аккаунтов Kaiten может не быть email — возвращаем null, не падаем.
        if (value == null || value.isBlank()) return null;
        try {
            return new Email(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Удобный «опционально» геттер для тестов. */
    default Optional<KaitenUser> toDomainOptional(KaitenUserDto dto) {
        return dto == null ? Optional.empty() : Optional.of(toDomain(dto));
    }
}
