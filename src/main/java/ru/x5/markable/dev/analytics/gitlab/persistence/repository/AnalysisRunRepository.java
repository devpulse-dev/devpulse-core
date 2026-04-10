package ru.x5.markable.dev.analytics.gitlab.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.AnalysisRun;

/**
 * Репозиторий для работы с сущностью {@link AnalysisRun}.
 * 
 * <p>Предоставляет методы для выполнения CRUD операций над записями о запусках анализа
 * репозиториев Git. Наследует стандартные методы из {@link JpaRepository}.</p>
 * 
 * <p>Использует UUID в качестве идентификатора сущности.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see AnalysisRun
 * @see JpaRepository
 */
public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, UUID> {

}
