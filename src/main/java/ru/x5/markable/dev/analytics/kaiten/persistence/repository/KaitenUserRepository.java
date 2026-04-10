package ru.x5.markable.dev.analytics.kaiten.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenUser;

import java.util.Optional;

/**
 * Репозиторий для работы с сущностью {@link KaitenUser}.
 * 
 * <p>Предоставляет методы для выполнения CRUD операций над записями о пользователях Kaiten,
 * а также специализированные методы для поиска по электронной почте.</p>
 * 
 * <p>Использует Long в качестве идентификатора сущности.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see KaitenUser
 * @see JpaRepository
 */
@Repository
public interface KaitenUserRepository extends JpaRepository<KaitenUser, Long> {

    /**
     * Находит пользователя по электронной почте.
     * 
     * @param email электронная почта пользователя
     * @return Optional с найденным пользователем, или пустой Optional если пользователь не найден
     */
    Optional<KaitenUser> findByEmail(String email);
}
