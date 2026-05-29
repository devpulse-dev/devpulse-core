package ru.x5.devpulse.adapter.persistence.kaiten;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.x5.devpulse.application.port.out.KaitenUserRepository;
import ru.x5.devpulse.domain.model.kaiten.KaitenUser;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.KaitenUserId;

@Component
@Log4j2
@RequiredArgsConstructor
class KaitenUserRepositoryAdapter implements KaitenUserRepository {

    private final KaitenUserJpaRepository jpa;
    private final KaitenUserEntityMapper mapper;

    @Override
    public Optional<KaitenUser> findById(KaitenUserId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<KaitenUser> findByEmail(Email email) {
        return jpa.findByEmail(email.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void upsertAll(Collection<KaitenUser> users) {
        if (users == null || users.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        for (KaitenUser u : users) {
            KaitenUserEntity entity = mapper.toEntity(u);
            entity.setLastSyncedAt(now);
            jpa.save(entity);   // id уже стоит → JPA merge
        }
        log.debug("Upserted {} kaiten users", users.size());
    }
}
