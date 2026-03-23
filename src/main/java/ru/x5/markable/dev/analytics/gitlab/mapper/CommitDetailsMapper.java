package ru.x5.markable.dev.analytics.gitlab.mapper;

import java.time.LocalDateTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.x5.markable.dev.analytics.gitlab.model.CommitDetail;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.CommitDetails;

import static java.time.LocalDateTime.now;

@Mapper(componentModel = "spring")
public interface CommitDetailsMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "commitHash", source = "hash")
    @Mapping(target = "hour", source = "commitDate", qualifiedByName = "extractHour")
    @Mapping(target = "addedLines", source = "added")
    @Mapping(target = "deletedLines", source = "deleted")
    @Mapping(target = "testAddedLines", source = "testAdded")
    @Mapping(target = "repositoryName", source = "repoName")
    @Mapping(target = "isMerge", source = "merge")
    @Mapping(target = "collectedAt", source = "commitDetail", qualifiedByName = "currentDateTime")
    CommitDetails toEntity(CommitDetail commitDetail);

    @Named("extractHour")
    default Integer extractHour(LocalDateTime commitDate) {
        return commitDate != null ? commitDate.getHour() : null;
    }

    @Named("currentDateTime")
    default LocalDateTime currentDateTime(CommitDetail commitDetail) {
        return now();
    }
}
