package ru.x5.devpulse.adapter.persistence.kaiten;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.x5.devpulse.domain.model.kaiten.KaitenCard;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardId;
import ru.x5.devpulse.domain.model.user.KaitenUserId;

/**
 * Mapping JPA entity ↔ {@link KaitenCard}.
 *
 * <p><b>Legacy:</b> use case'ы больше не пишут/не читают карточки через эту таблицу
 * (карточки тянутся live из Kaiten API в профиле). Этот mapper нужен только для
 * сохранения compile-safety и потенциального возврата к кэшированию в БД в будущем.</p>
 *
 * <p>Поля domain'а {@code columnType} и {@code columnTitle} в БД-схеме отсутствуют,
 * поэтому при чтении из БД они мапятся в {@code null}. {@code typeId} есть и в entity,
 * и в domain — мапим из {@code Long} в {@code Integer}.</p>
 */
@Mapper(componentModel = "spring")
public interface KaitenCardEntityMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = "toCardId")
    @Mapping(target = "ownerId", source = "ownerId", qualifiedByName = "toUserId")
    @Mapping(target = "typeId", source = "typeId", qualifiedByName = "longToInt")
    @Mapping(target = "columnType", ignore = true)   // нет колонки в БД
    @Mapping(target = "columnTitle", source = "columnName") // используем columnName как best-effort
    @Mapping(target = "memberIds", expression = "java(java.util.List.of())")
    KaitenCard toDomain(KaitenCardEntity entity);

    @Mapping(target = "id", source = "id.value")
    @Mapping(target = "ownerId", expression = "java(domain.ownerId() == null ? null : domain.ownerId().value())")
    @Mapping(target = "typeId", expression = "java(domain.typeId() == null ? null : domain.typeId().longValue())")
    @Mapping(target = "columnName", source = "columnTitle")
    @Mapping(target = "status", ignore = true)       // нет в domain больше
    @Mapping(target = "priority", ignore = true)
    @Mapping(target = "spaceId", ignore = true)
    @Mapping(target = "boardId", ignore = true)
    @Mapping(target = "typeName", ignore = true)
    @Mapping(target = "columnId", ignore = true)
    @Mapping(target = "laneId", ignore = true)
    @Mapping(target = "laneName", ignore = true)
    @Mapping(target = "lastMovedAt", ignore = true)
    @Mapping(target = "laneChangedAt", ignore = true)
    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "version", ignore = true)
    KaitenCardEntity toEntity(KaitenCard domain);

    /**
     * Доменный {@link KaitenCard} с подмешанным списком member-id из join-таблицы.
     * Используется адаптером после двух SELECT'ов (карточки + члены).
     */
    default KaitenCard withMembers(KaitenCardEntity entity, List<KaitenUserId> members) {
        KaitenCard base = toDomain(entity);
        return new KaitenCard(
                base.id(),
                base.title(),
                base.description(),
                base.typeId(),
                base.columnType(),
                base.columnTitle(),
                base.boardName(),
                base.spaceName(),
                base.ownerId(),
                base.ownerName(),
                base.createdAt(),
                base.updatedAt(),
                base.closedAt(),
                base.archived(),
                base.url(),
                members
        );
    }

    @Named("toCardId")
    default KaitenCardId toCardId(Long v) {
        return v == null ? null : new KaitenCardId(v);
    }

    @Named("toUserId")
    default KaitenUserId toUserId(Long v) {
        return v == null ? null : new KaitenUserId(v);
    }

    @Named("longToInt")
    default Integer longToInt(Long v) {
        return v == null ? null : v.intValue();
    }
}
