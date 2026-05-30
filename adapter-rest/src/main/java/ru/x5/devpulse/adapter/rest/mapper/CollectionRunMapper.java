package ru.x5.devpulse.adapter.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import ru.x5.devpulse.adapter.rest.api.model.CollectionRun;

/**
 * {@code domain.collection.CollectionRun} → {@link CollectionRun}.
 *
 * <p>Все поля совпадают по имени и типу. {@code CollectionStatus} → {@code StatusEnum}
 * мапится автоматически по имени значения (RUNNING/SUCCESS/FAILED).</p>
 */
@Mapper(componentModel = "spring",
        implementationName = "RestCollectionRunMapperImpl",
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface CollectionRunMapper {

    CollectionRun toDto(ru.x5.devpulse.domain.model.collection.CollectionRun r);
}
