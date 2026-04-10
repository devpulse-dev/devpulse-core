package ru.x5.markable.dev.analytics.gitlab.mapper;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.AuthorStats;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.AnalysisResponse;

/**
 * MapStruct маппер для преобразования сущности {@link AuthorStats} в DTO {@link AnalysisResponse}.
 * 
 * <p>Использует Spring component model для интеграции с контекстом Spring.
 * Предоставляет методы для преобразования отдельных объектов и списков.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see AuthorStats
 * @see AnalysisResponse
 */
@Mapper(componentModel = "spring")
public interface AuthorStatsMapper {
    
    /**
     * Преобразует сущность статистики автора в DTO ответа анализа.
     * 
     * <p>Выполняет маппинг полей с использованием аннотаций @Mapping для указания
     * соответствия между полями источника и назначения.</p>
     * 
     * @param source сущность статистики автора
     * @return DTO ответа анализа
     */
    @Mapping(target = "email", source = "email")
    @Mapping(target = "mergeCommits", source = "mergeCommits")
    @Mapping(target = "commits", source = "commits")
    @Mapping(target = "added", source = "addedLines")
    @Mapping(target = "deleted", source = "deletedLines")
    @Mapping(target = "testAdded", source = "testAddedLines")
    AnalysisResponse toDto(AuthorStats source);

    /**
     * Преобразует список сущностей статистики авторов в список DTO ответов анализа.
     * 
     * @param source список сущностей статистики авторов
     * @return список DTO ответов анализа
     */
    List<AnalysisResponse> toDto(List<AuthorStats> source);
}
