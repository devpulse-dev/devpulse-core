package ru.x5.markable.dev.analytics.adapter.persistence.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface UnifiedUserJpaRepository extends JpaRepository<UnifiedUserEntity, Long> {

    Optional<UnifiedUserEntity> findByEmail(String email);

    List<UnifiedUserEntity> findByEmailIn(Collection<String> emails);
}
