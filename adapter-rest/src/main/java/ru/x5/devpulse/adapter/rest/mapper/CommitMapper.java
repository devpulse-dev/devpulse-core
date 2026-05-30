package ru.x5.devpulse.adapter.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import ru.x5.devpulse.adapter.rest.api.model.Commit;

/**
 * {@code domain.git.Commit} → {@link Commit}.
 *
 * <p>Value-объекты ({@code CommitHash}, {@code Email}, {@code RepoName}, {@code TaskNumber})
 * разворачиваются конвертерами. {@code long → Integer} — тоже через конвертер.</p>
 */
@Mapper(componentModel = "spring",
        implementationName = "RestCommitMapperImpl",
        uses = DomainTypeConverters.class,
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface CommitMapper {

    Commit toDto(ru.x5.devpulse.domain.model.git.Commit c);
}
