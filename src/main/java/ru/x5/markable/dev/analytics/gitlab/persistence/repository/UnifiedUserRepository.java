package ru.x5.markable.dev.analytics.gitlab.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.UnifiedUser;

import java.util.Optional;

@Repository
public interface UnifiedUserRepository extends JpaRepository<UnifiedUser, Long> {

    Optional<UnifiedUser> findByEmail(String email);

    Optional<UnifiedUser> findByKaitenId(Long kaitenId);

    Optional<UnifiedUser> findByGitlabId(Integer gitlabId);

    boolean existsByEmail(String email);
}
