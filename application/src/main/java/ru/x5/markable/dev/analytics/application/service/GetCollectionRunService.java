package ru.x5.markable.dev.analytics.application.service;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ru.x5.markable.dev.analytics.application.port.in.GetCollectionRunUseCase;
import ru.x5.markable.dev.analytics.application.port.out.CollectionRunRepository;
import ru.x5.markable.dev.analytics.domain.model.collection.CollectionRun;

/** Тонкая делегация к {@link CollectionRunRepository}. */
@RequiredArgsConstructor
public final class GetCollectionRunService implements GetCollectionRunUseCase {

    private final CollectionRunRepository collectionRunRepository;

    @Override
    public Optional<CollectionRun> findById(UUID id) {
        return collectionRunRepository.findById(id);
    }
}
