package ru.x5.markable.dev.analytics.adapter.persistence.kaiten;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.x5.markable.dev.analytics.domain.model.kaiten.KaitenCard;
import ru.x5.markable.dev.analytics.domain.model.kaiten.KaitenCardId;
import ru.x5.markable.dev.analytics.domain.model.user.KaitenUserId;

@Mapper(componentModel = "spring")
public interface KaitenCardEntityMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = "toCardId")
    @Mapping(target = "ownerId", source = "ownerId", qualifiedByName = "toUserId")
    @Mapping(target = "memberIds", expression = "java(java.util.List.of())")
    KaitenCard toDomain(KaitenCardEntity entity);

    @Mapping(target = "id", source = "id.value")
    @Mapping(target = "ownerId", expression = "java(domain.ownerId() == null ? null : domain.ownerId().value())")
    @Mapping(target = "priority", ignore = true)
    @Mapping(target = "spaceId", ignore = true)
    @Mapping(target = "boardId", ignore = true)
    @Mapping(target = "typeId", ignore = true)
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
                base.status(),
                base.columnName(),
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
}
