package ru.x5.devpulse.adapter.reviews;

import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import ru.x5.devpulse.adapter.reviews.dto.GitlabApprovalsDto;
import ru.x5.devpulse.adapter.reviews.dto.GitlabMrDto;
import ru.x5.devpulse.adapter.reviews.dto.GitlabNoteDto;
import ru.x5.devpulse.adapter.reviews.dto.GitlabUserDto;

/**
 * Декларативный HTTP-клиент GitLab API v4 ({@code @HttpExchange}).
 *
 * <p>Путь проекта ({@code namespace/repo}) идёт как path-переменная {@code project} —
 * {@code EncodingMode.VALUES_ONLY} (см. {@link GitlabAdapterConfig}) закодирует {@code /} → {@code %2F}.
 * Пагинация — GitLab keyset/offset через {@code page}/{@code per_page}.</p>
 */
@HttpExchange(accept = "application/json")
public interface GitlabHttpClient {

    /** {@code GET /users?page=&per_page=&active=true} — пользователи (для public_email). */
    @GetExchange("/users")
    List<GitlabUserDto> getUsers(
            @RequestParam("page") int page,
            @RequestParam("per_page") int perPage,
            @RequestParam(value = "active", required = false) Boolean active);

    /**
     * {@code GET /projects/{project}/merge_requests} с фильтром по {@code updated_after}.
     * {@code scope=all}, {@code state=all} — все MR независимо от авторства/состояния.
     */
    @GetExchange("/projects/{project}/merge_requests")
    List<GitlabMrDto> getMergeRequests(
            @PathVariable("project") String project,
            @RequestParam("updated_after") String updatedAfter,
            @RequestParam("page") int page,
            @RequestParam("per_page") int perPage,
            @RequestParam("scope") String scope,
            @RequestParam("state") String state);

    /** {@code GET /projects/{project}/merge_requests/{iid}/approvals}. */
    @GetExchange("/projects/{project}/merge_requests/{iid}/approvals")
    GitlabApprovalsDto getApprovals(
            @PathVariable("project") String project,
            @PathVariable("iid") long iid);

    /** {@code GET /projects/{project}/merge_requests/{iid}/notes} (пагинация). */
    @GetExchange("/projects/{project}/merge_requests/{iid}/notes")
    List<GitlabNoteDto> getNotes(
            @PathVariable("project") String project,
            @PathVariable("iid") long iid,
            @RequestParam("page") int page,
            @RequestParam("per_page") int perPage);
}
