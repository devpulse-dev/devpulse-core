package ru.x5.devpulse.adapter.persistence.collection;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.x5.devpulse.domain.model.collection.CollectionRun;

@Mapper(componentModel = "spring")
interface CollectionRunEntityMapper {

    CollectionRun toDomain(CollectionRunEntity entity);

    // cancel_requested — run-control флаг, не часть домена; управляется отдельными
    // markCancelRequested/isCancelRequested, маппером не трогается.
    @Mapping(target = "cancelRequested", ignore = true)
    CollectionRunEntity toEntity(CollectionRun domain);
}
