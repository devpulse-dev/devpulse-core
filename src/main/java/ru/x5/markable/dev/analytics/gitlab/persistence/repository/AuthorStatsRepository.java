package ru.x5.markable.dev.analytics.gitlab.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.AuthorStats;

/**
 * Репозиторий для работы с сущностью {@link AuthorStats}.
 * 
 * <p>Предоставляет методы для выполнения CRUD операций над записями о статистике авторов.
 * Наследует стандартные методы из {@link JpaRepository}.</p>
 * 
 * <p>Использует Long в качестве идентификатора сущности.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see AuthorStats
 * @see JpaRepository
 */
public interface AuthorStatsRepository extends JpaRepository<AuthorStats, Long> {

}
