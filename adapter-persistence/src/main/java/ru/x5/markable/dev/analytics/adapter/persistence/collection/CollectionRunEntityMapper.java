package ru.x5.markable.dev.analytics.adapter.persistence.collection;

import org.mapstruct.Mapper;
import ru.x5.markable.dev.analytics.domain.model.collection.CollectionRun;

@Mapper(componentModel = "spring")
interface CollectionRunEntityMapper {

    CollectionRun toDomain(CollectionRunEntity entity);

    CollectionRunEntity toEntity(CollectionRun domain);
}
