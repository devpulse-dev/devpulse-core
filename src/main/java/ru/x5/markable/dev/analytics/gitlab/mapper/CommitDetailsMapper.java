package ru.x5.markable.dev.analytics.gitlab.mapper;

import java.time.LocalDateTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.x5.markable.dev.analytics.gitlab.model.CommitDetail;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.CommitDetails;

import static java.time.LocalDateTime.now;

/**
 * MapStruct маппер для преобразования модели {@link CommitDetail} в сущность {@link CommitDetails}.
 * 
 * <p>Использует Spring component model для интеграции с контекстом Spring.
 * Предоставляет методы для преобразования с использованием кастомных методов для извлечения
 * часа из даты коммита и установки текущего времени.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see CommitDetail
 * @see CommitDetails
 */
@Mapper(componentModel = "spring")
public interface CommitDetailsMapper {

    /**
     * Преобразует модель деталей коммита в сущность для сохранения в базе данных.
     * 
     * <p>Выполняет маппинг полей с использованием аннотаций @Mapping для указания
     * соответствия между полями источника и назначения. Некоторые поля игнорируются
     * или заполняются с использованием кастомных методов.</p>
     * 
     * @param commitDetail модель деталей коммита
     * @return сущность деталей коммита для сохранения в базе данных
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "commitHash", source = "hash")
    @Mapping(target = "hour", source = "commitDate", qualifiedByName = "extractHour")
    @Mapping(target = "addedLines", source = "added")
    @Mapping(target = "deletedLines", source = "deleted")
    @Mapping(target = "testAddedLines", source = "testAdded")
    @Mapping(target = "repositoryName", source = "repoName")
    @Mapping(target = "isMerge", source = "merge")
    @Mapping(target = "collectedAt", source = "commitDetail", qualifiedByName = "currentDateTime")
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "kaitenCardId", ignore = true)
    CommitDetails toEntity(CommitDetail commitDetail);

    /**
     * Извлекает час из даты и времени коммита.
     * 
     * <p>Используется как кастомный метод маппинга для заполнения поля hour.</p>
     * 
     * @param commitDate дата и время коммита
     * @return час коммита, или null если дата равна null
     */
    @Named("extractHour")
    default Integer extractHour(LocalDateTime commitDate) {
        return commitDate != null ? commitDate.getHour() : null;
    }

    /**
     * Возвращает текущую дату и время.
     * 
     * <p>Используется как кастомный метод маппинга для заполнения поля collectedAt.</p>
     * 
     * @param commitDetail модель деталей коммита (не используется)
     * @return текущая дата и время
     */
    @Named("currentDateTime")
    default LocalDateTime currentDateTime(CommitDetail commitDetail) {
        return now();
    }
}
