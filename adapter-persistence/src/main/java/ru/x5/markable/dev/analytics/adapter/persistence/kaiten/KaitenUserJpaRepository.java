package ru.x5.markable.dev.analytics.adapter.persistence.kaiten;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface KaitenUserJpaRepository extends JpaRepository<KaitenUserEntity, Long> {

    Optional<KaitenUserEntity> findByEmail(String email);
}
