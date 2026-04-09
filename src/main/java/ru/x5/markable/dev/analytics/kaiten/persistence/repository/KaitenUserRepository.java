package ru.x5.markable.dev.analytics.kaiten.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenUser;

import java.util.Optional;

@Repository
public interface KaitenUserRepository extends JpaRepository<KaitenUser, Long> {

    Optional<KaitenUser> findByEmail(String email);
}
