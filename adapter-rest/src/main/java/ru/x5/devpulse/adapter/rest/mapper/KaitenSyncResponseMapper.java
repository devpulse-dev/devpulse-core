package ru.x5.devpulse.adapter.rest.mapper;

import org.mapstruct.Mapper;
import ru.x5.devpulse.adapter.rest.api.model.KaitenSyncResponse;

/**
 * {@code int synced} → {@link KaitenSyncResponse}. Тривиальная обёртка для единообразия.
 */
@Mapper(componentModel = "spring", implementationName = "RestKaitenSyncResponseMapperImpl")
public interface KaitenSyncResponseMapper {

    default KaitenSyncResponse toDto(int synced) {
        return new KaitenSyncResponse(synced);
    }
}
