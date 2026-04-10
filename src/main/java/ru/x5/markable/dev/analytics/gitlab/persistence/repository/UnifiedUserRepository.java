package ru.x5.markable.dev.analytics.gitlab.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.UnifiedUser;

import java.util.Optional;

/**
 * Репозиторий для работы с сущностью {@link UnifiedUser}.
 * 
 * <p>Предоставляет методы для выполнения CRUD операций над записями о унифицированных пользователях,
 * которые объединяют информацию из GitLab и Kaiten, а также специализированные методы для поиска
 * по различным идентификаторам и электронной почте.</p>
 * 
 * <p>Использует Long в качестве идентификатора сущности.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see UnifiedUser
 * @see JpaRepository
 */
@Repository
public interface UnifiedUserRepository extends JpaRepository<UnifiedUser, Long> {

    /**
     * Находит пользователя по электронной почте.
     * 
     * @param email электронная почта пользователя
     * @return Optional с найденным пользователем, или пустой Optional если пользователь не найден
     */
    Optional<UnifiedUser> findByEmail(String email);

    /**
     * Находит пользователя по идентификатору в Kaiten.
     * 
     * @param kaitenId идентификатор пользователя в Kaiten
     * @return Optional с найденным пользователем, или пустой Optional если пользователь не найден
     */
    Optional<UnifiedUser> findByKaitenId(Long kaitenId);

    /**
     * Находит пользователя по идентификатору в GitLab.
     * 
     * @param gitlabId идентификатор пользователя в GitLab
     * @return Optional с найденным пользователем, или пустой Optional если пользователь не найден
     */
    Optional<UnifiedUser> findByGitlabId(Integer gitlabId);

    /**
     * Проверяет существование пользователя с указанной электронной почтой.
     * 
     * @param email электронная почта пользователя
     * @return true если пользователь существует, false в противном случае
     */
    boolean existsByEmail(String email);
}
