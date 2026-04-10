package ru.x5.markable.dev.analytics.gitlab.interactor.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.x5.markable.dev.analytics.commons.exceptions.UnprocessableEntityException;
import ru.x5.markable.dev.analytics.gitlab.exception.AnalysisException;
import ru.x5.markable.dev.analytics.gitlab.exception.RepositoryAnalysisException;
import ru.x5.markable.dev.analytics.gitlab.exception.StatisticsPersistenceException;
import ru.x5.markable.dev.analytics.gitlab.interactor.AnalysisInteractor;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.AuthorStats;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.AnalysisRequest;
import ru.x5.markable.dev.analytics.gitlab.service.AnalysisService;

import static ru.x5.markable.dev.analytics.gitlab.interactor.Message.ANALYZE_REPOSITORY_ERROR;
import static ru.x5.markable.dev.analytics.gitlab.interactor.Message.GIT_ERROR;
import static ru.x5.markable.dev.analytics.gitlab.interactor.Message.STATISTICS_SAVE_ERROR;

/**
 * Реализация интерактора для запуска анализа Git-репозиториев.
 * 
 * <p>Преобразует исключения сервисного слоя в API исключения и делегирует выполнение
 * анализа сервису {@link AnalysisService}.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class AnalysisInteractorImpl implements AnalysisInteractor {

    /**
     * Сервис для анализа репозиториев.
     */
    private final AnalysisService analysisService;

    /**
     * Запускает анализ Git-репозиториев.
     * 
     * <p>Делегирует выполнение анализа сервису и преобразует исключения сервисного слоя
     * в API исключения с соответствующими сообщениями об ошибках.</p>
     * 
     * @param request запрос на анализ с указанием периодов и репозиториев
     * @return список статистики авторов
     * @throws UnprocessableEntityException если произошла ошибка при анализе репозитория,
     *                                        сохранении статистики или выполнении Git-команды
     */
    @Override
    public List<AuthorStats> startAnalysis(AnalysisRequest request) {
        try {
            return analysisService.startAnalysis(request);
        } catch (RepositoryAnalysisException e) {
            throw new UnprocessableEntityException(ANALYZE_REPOSITORY_ERROR, e.getRepository());
        } catch (StatisticsPersistenceException e) {
            throw new UnprocessableEntityException(STATISTICS_SAVE_ERROR);
        } catch (AnalysisException e) {
            throw new UnprocessableEntityException(GIT_ERROR);
        }
    }
}
