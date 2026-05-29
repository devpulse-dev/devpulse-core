package ru.x5.devpulse.adapter.persistence.collection;

import org.mapstruct.Mapper;
import ru.x5.devpulse.domain.model.collection.CollectionRun;

@Mapper(componentModel = "spring")
interface CollectionRunEntityMapper {

    CollectionRun toDomain(CollectionRunEntity entity);

    CollectionRunEntity toEntity(CollectionRun domain);
}
