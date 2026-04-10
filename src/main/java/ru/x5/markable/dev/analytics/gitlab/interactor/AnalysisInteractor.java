package ru.x5.markable.dev.analytics.gitlab.interactor;

import java.util.List;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.AuthorStats;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.AnalysisRequest;

/**
 * Интерактор для запуска анализа Git-репозиториев.
 * 
 * <p>Определяет контракт для запуска анализа репозиториев и получения статистики авторов.
 * Является частью бизнес-логики приложения и преобразует исключения сервисного слоя
 * в API исключения.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
public interface AnalysisInteractor {

   /**
    * Запускает анализ Git-репозиториев.
    * 
    * @param request запрос на анализ с указанием периодов и репозиториев
    * @return список статистики авторов
    */
   List<AuthorStats> startAnalysis(AnalysisRequest request);

}
