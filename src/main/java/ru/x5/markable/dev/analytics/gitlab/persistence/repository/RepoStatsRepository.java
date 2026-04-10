package ru.x5.markable.dev.analytics.gitlab.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.RepoStats;

/**
 * Репозиторий для работы с сущностью {@link RepoStats}.
 * 
 * <p>Предоставляет методы для выполнения CRUD операций над записями о статистике репозиториев.
 * Наследует стандартные методы из {@link JpaRepository}.</p>
 * 
 * <p>Использует Long в качестве идентификатора сущности.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see RepoStats
 * @see JpaRepository
 */
public interface RepoStatsRepository extends JpaRepository<RepoStats, Long> {

}
