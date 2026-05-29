package ru.x5.devpulse.adapter.persistence.commit;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.x5.devpulse.domain.common.TaskNumber;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.git.CommitHash;
import ru.x5.devpulse.domain.model.git.RepoName;
import ru.x5.devpulse.domain.model.user.Email;

@Mapper(componentModel = "spring")
interface CommitEntityMapper {

    @Mapping(target = "hash", source = "commitHash", qualifiedByName = "toCommitHash")
    @Mapping(target = "authorEmail", source = "email", qualifiedByName = "toEmail")
    @Mapping(target = "addedLines", source = "addedLines")
    @Mapping(target = "deletedLines", source = "deletedLines")
    @Mapping(target = "testAddedLines", source = "testAddedLines")
    @Mapping(target = "message", source = "commitMessage")
    @Mapping(target = "taskNumber", source = "taskNumber", qualifiedByName = "toTaskNumber")
    @Mapping(target = "repo", source = "repositoryName", qualifiedByName = "toRepoName")
    Commit toDomain(CommitDetailsEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "commitHash", source = "commit.hash.value")
    @Mapping(target = "email", source = "commit.authorEmail.value")
    @Mapping(target = "commitDate", source = "commit.commitDate")
    @Mapping(target = "hour", expression = "java(commit.commitDate().getHour())")
    @Mapping(target = "merge", source = "commit.merge")
    @Mapping(target = "addedLines", source = "commit.addedLines")
    @Mapping(target = "deletedLines", source = "commit.deletedLines")
    @Mapping(target = "testAddedLines", source = "commit.testAddedLines")
    @Mapping(target = "commitMessage", source = "commit.message")
    @Mapping(target = "taskNumber", expression = "java(commit.taskNumber() == null ? null : commit.taskNumber().value())")
    @Mapping(target = "repositoryName", source = "commit.repo.value")
    @Mapping(target = "collectedAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "kaitenCardId", expression = "java(commit.taskNumber() == null ? null : commit.taskNumber().asKaitenCardId().stream().boxed().findFirst().orElse(null))")
    CommitDetailsEntity toEntity(Commit commit, Long userId);

    @Named("toCommitHash")
    default CommitHash toCommitHash(String v) {
        return v == null ? null : new CommitHash(v);
    }

    @Named("toEmail")
    default Email toEmail(String v) {
        return v == null ? null : new Email(v);
    }

    @Named("toTaskNumber")
    default TaskNumber toTaskNumber(String v) {
        return (v == null || v.isBlank()) ? null : new TaskNumber(v);
    }

    @Named("toRepoName")
    default RepoName toRepoName(String v) {
        return v == null ? null : new RepoName(v);
    }
}
