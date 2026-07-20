package ru.x5.devpulse.adapter.kaiten;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.x5.devpulse.adapter.kaiten.dto.KaitenCardDto;
import ru.x5.devpulse.adapter.kaiten.dto.KaitenMemberDto;
import ru.x5.devpulse.adapter.kaiten.dto.KaitenUserDto;
import ru.x5.devpulse.domain.model.kaiten.KaitenCard;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardId;
import ru.x5.devpulse.domain.model.kaiten.KaitenUrgency;
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
    @Mapping(target = "typeId", source = "dto.typeId")
    @Mapping(target = "columnType", source = "dto.column.type")
    @Mapping(target = "columnTitle", source = "dto.column.title")
    @Mapping(target = "boardName", source = "dto.board.name")
    @Mapping(target = "spaceName", source = "dto.space.name")
    @Mapping(target = "ownerId", source = "dto.owner", qualifiedByName = "ownerToKaitenUserId")
    @Mapping(target = "ownerName", source = "dto.owner.fullName")
    @Mapping(target = "url", expression = "java(buildCardUrl(webBaseUrl, dto.id()))")
    @Mapping(target = "memberIds", source = "dto.members", qualifiedByName = "membersToIds")
    @Mapping(target = "urgency", expression = "java(urgencyFrom(dto.properties()))")
    @Mapping(target = "parentId", expression = "java(parentIdFrom(dto.parents()))")
    @Mapping(target = "parentTitle", expression = "java(parentTitleFrom(dto.parents()))")
    @Mapping(target = "parentUrl", expression = "java(parentUrlFrom(dto.parents(), webBaseUrl))")
    @Mapping(target = "inProgressAt", source = "dto.inProgressAt")
    @Mapping(target = "doneAt", source = "dto.doneAt")
    @Mapping(target = "aiAgent", expression = "java(aiAgentFrom(dto.properties()))")
    KaitenCard toDomain(KaitenCardDto dto, String webBaseUrl);

    /** Срочность из property {@code id_2561} (массив; берём первый код). */
    default KaitenUrgency urgencyFrom(Map<String, Object> properties) {
        if (properties == null) return KaitenUrgency.UNKNOWN;
        return KaitenUrgency.fromId(firstInt(properties.get("id_2561")));
    }

    /**
     * Галка «AI-Agent» из property {@code id_6064}: {@code true} только при явном true
     * (может отсутствовать или быть false). Терпим к форме: {@code true}/{@code [true]}/{@code "true"}/{@code 1}.
     */
    default boolean aiAgentFrom(Map<String, Object> properties) {
        if (properties == null) return false;
        Object v = properties.get("id_6064");
        if (v instanceof List<?> list) {
            v = list.isEmpty() ? null : list.get(0);
        }
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return "true".equalsIgnoreCase(s.trim());
        if (v instanceof Number n) return n.intValue() != 0;
        return false;
    }

    default KaitenCardId parentIdFrom(List<KaitenCardDto.KaitenParentDto> parents) {
        return (parents == null || parents.isEmpty()) ? null : new KaitenCardId(parents.get(0).id());
    }

    default String parentTitleFrom(List<KaitenCardDto.KaitenParentDto> parents) {
        return (parents == null || parents.isEmpty()) ? null : parents.get(0).title();
    }

    default String parentUrlFrom(List<KaitenCardDto.KaitenParentDto> parents, String webBaseUrl) {
        return (parents == null || parents.isEmpty()) ? null : buildCardUrl(webBaseUrl, parents.get(0).id());
    }

    /** {@code [4524]} / {@code 4524} / {@code "4524"} → {@code 4524}; иначе {@code null}. */
    private static Integer firstInt(Object value) {
        Object v = value;
        if (v instanceof List<?> list) {
            if (list.isEmpty()) return null;
            v = list.get(0);
        }
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try {
                return Integer.valueOf(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Собирает web-URL карточки: {@code {webBaseUrl}/{cardId}}. Если baseUrl не задан —
     * возвращает {@code null} (фронт не покажет ссылку, но всё остальное работает).
     * Хвостовые слэши в baseUrl нормализуются ({@code .../}, {@code ...//} → один).
     */
    default String buildCardUrl(String webBaseUrl, long cardId) {
        if (webBaseUrl == null || webBaseUrl.isBlank()) return null;
        String trimmed = webBaseUrl.replaceAll("/+$", "");
        return trimmed + "/" + cardId;
    }

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
