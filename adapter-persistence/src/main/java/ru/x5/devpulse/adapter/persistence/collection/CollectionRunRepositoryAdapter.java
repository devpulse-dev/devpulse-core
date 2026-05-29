package ru.x5.devpulse.adapter.persistence.collection;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.x5.devpulse.application.port.out.CollectionRunRepository;
import ru.x5.devpulse.domain.model.collection.CollectionRun;

@Component
@RequiredArgsConstructor
class CollectionRunRepositoryAdapter implements CollectionRunRepository {

    private final CollectionRunJpaRepository jpa;
    private final CollectionRunEntityMapper mapper;

    @Override
    @Transactional
    public void save(CollectionRun run) {
        jpa.save(mapper.toEntity(run));
    }

    @Override
    public Optional<CollectionRun> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<LocalDateTime> findLastSuccessfulUntil() {
        return jpa.findLastSuccessfulUntil();
    }
}
