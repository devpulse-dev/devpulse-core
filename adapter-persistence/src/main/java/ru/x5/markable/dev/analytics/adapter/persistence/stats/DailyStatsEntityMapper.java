package ru.x5.markable.dev.analytics.adapter.persistence.stats;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.x5.markable.dev.analytics.domain.model.git.RepoName;
import ru.x5.markable.dev.analytics.domain.model.stats.DailyAuthorStats;
import ru.x5.markable.dev.analytics.domain.model.user.Email;

@Mapper(componentModel = "spring")
interface DailyStatsEntityMapper {

    @Mapping(target = "authorEmail", source = "email", qualifiedByName = "toEmail")
    @Mapping(target = "repo", source = "repositoryName", qualifiedByName = "toRepoName")
    DailyAuthorStats toDomain(DailyAuthorStatsEntity entity);

    @Mapping(target = "email", source = "authorEmail.value")
    @Mapping(target = "repositoryName", source = "repo.value")
    DailyAuthorStatsEntity toEntity(DailyAuthorStats domain);

    @Named("toEmail")
    default Email toEmail(String v) {
        return v == null ? null : new Email(v);
    }

    @Named("toRepoName")
    default RepoName toRepoName(String v) {
        return v == null ? null : new RepoName(v);
    }
}
