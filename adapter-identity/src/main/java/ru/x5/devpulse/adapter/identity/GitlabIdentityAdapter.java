package ru.x5.devpulse.adapter.identity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import ru.x5.devpulse.adapter.gitlab.GitRepoProperties;
import ru.x5.devpulse.adapter.gitlab.GitlabHttpClient;
import ru.x5.devpulse.adapter.gitlab.GitlabProjectPaths;
import ru.x5.devpulse.adapter.gitlab.GitlabProperties;
import ru.x5.devpulse.adapter.gitlab.dto.GitlabCurrentUserDto;
import ru.x5.devpulse.adapter.gitlab.dto.GitlabMemberDto;
import ru.x5.devpulse.application.port.out.GitIdentityProvider;
import ru.x5.devpulse.application.port.out.GitUnavailableException;
import ru.x5.devpulse.application.port.out.InvalidGitTokenException;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.GitIdentity;
import ru.x5.devpulse.domain.model.user.GitTokenType;

/**
 * {@link GitIdentityProvider} поверх GitLab (ADR-13).
 *
 * <ul>
 *   <li>{@code fetchIdentity} — {@code GET /user} <b>токеном пользователя</b> (per-call header:
 *       PAT → {@code PRIVATE-TOKEN}, OAuth → {@code Authorization: Bearer}) через
 *       {@code gitlabUserRestClient} (без дефолтного токена).</li>
 *   <li>{@code maxProjectAccessLevel} — {@code GET /projects/:id/members/all/:uid}
 *       <b>сервисным</b> токеном через общий {@link GitlabHttpClient}; max по проектам из
 *       {@link GitlabProjectPaths}.</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
class GitlabIdentityAdapter implements GitIdentityProvider {

    private final RestClient gitlabUserRestClient;
    private final GitlabHttpClient http;
    private final GitlabProperties properties;
    private final GitRepoProperties gitRepos;

    @Override
    public GitIdentity fetchIdentity(String token, GitTokenType type) {
        GitlabCurrentUserDto dto;
        try {
            dto = gitlabUserRestClient.get()
                    .uri("/user")
                    .headers(h -> applyAuth(h, token, type))
                    .retrieve()
                    .body(GitlabCurrentUserDto.class);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            throw new InvalidGitTokenException("GitLab отклонил токен: " + e.getStatusCode(), e);
        } catch (ResourceAccessException | HttpServerErrorException e) {
            // Сетевой сбой / 5xx — токен проверить не удалось (≠ невалидный токен).
            throw new GitUnavailableException("GitLab недоступен — не удалось проверить токен", e);
        }
        if (dto == null || dto.id() == null) {
            throw new InvalidGitTokenException("GitLab /user вернул пустой ответ");
        }
        return new GitIdentity(
                Math.toIntExact(dto.id()),
                resolveEmail(dto),
                dto.username(),
                dto.name(),
                dto.avatarUrl());
    }

    @Override
    public int maxProjectAccessLevel(int gitlabUserId) {
        int max = 0;
        for (String project : GitlabProjectPaths.resolve(properties, gitRepos)) {
            try {
                GitlabMemberDto member = http.getProjectMember(project, gitlabUserId);
                if (member != null && member.accessLevel() != null) {
                    max = Math.max(max, member.accessLevel());
                }
            } catch (HttpClientErrorException.NotFound e) {
                // не участник этого проекта — нормальный кейс, пропускаем
            } catch (HttpClientErrorException e) {
                log.warn("GitLab membership user={} проект={}: {}", gitlabUserId, project, e.getStatusCode());
            }
        }
        return max;
    }

    /** email из {@code GET /user}; пусто → fallback {@code username@домен} (как в reviews-резолве). */
    private Email resolveEmail(GitlabCurrentUserDto dto) {
        String email = (dto.email() != null && !dto.email().isBlank())
                ? dto.email()
                : dto.username() + "@" + properties.emailDomain();
        return new Email(email);
    }

    private static void applyAuth(HttpHeaders headers, String token, GitTokenType type) {
        if (type == GitTokenType.OAUTH) {
            headers.setBearerAuth(token);
        } else {
            headers.set("PRIVATE-TOKEN", token);
        }
    }
}
