package ru.x5.devpulse.adapter.reviews;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import ru.x5.devpulse.adapter.gitlab.GitRepoProperties;
import ru.x5.devpulse.adapter.gitlab.GitlabHttpClient;
import ru.x5.devpulse.adapter.gitlab.GitlabProjectPaths;
import ru.x5.devpulse.adapter.gitlab.GitlabProperties;
import ru.x5.devpulse.adapter.gitlab.GitlabRateLimiter;
import ru.x5.devpulse.adapter.gitlab.dto.GitlabApprovalsDto;
import ru.x5.devpulse.adapter.gitlab.dto.GitlabMrDto;
import ru.x5.devpulse.adapter.gitlab.dto.GitlabNoteDto;
import ru.x5.devpulse.adapter.gitlab.dto.GitlabUserDto;
import ru.x5.devpulse.application.port.out.ReviewGateway;
import ru.x5.devpulse.domain.model.review.CollectedMergeRequest;
import ru.x5.devpulse.domain.model.review.MrReview;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Реализация {@link ReviewGateway} поверх GitLab API.
 *
 * <p>Шаги: (1) подтягивает {@code public_email} всех юзеров; (2) по каждому проекту тянет
 * MR с {@code updated_after}; (3) для каждого MR — approvals + notes, агрегирует участие
 * ревьюеров (approve + объём не-системных комментов), резолвит email'ы, отбрасывает
 * саморевью. Все вызовы — через {@link GitlabRateLimiter}.</p>
 *
 * <p><b>Резолв email:</b> {@code public_email} если есть, иначе {@code username@домен}
 * (для scm.x5.ru эти значения совпадают). Неразрезолвленные/битые — пропускаются.</p>
 */
@Component
@Log4j2
@RequiredArgsConstructor
class ReviewGatewayAdapter implements ReviewGateway {

    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final GitlabHttpClient http;
    private final GitlabRateLimiter rateLimiter;
    private final GitlabProperties properties;
    private final GitRepoProperties gitRepos;

    @Override
    public List<CollectedMergeRequest> fetchMergeRequests(LocalDateTime updatedAfter) {
        List<String> projects = GitlabProjectPaths.resolve(properties, gitRepos);
        if (projects.isEmpty()) {
            log.warn("GitLab: не настроены проекты (gitlab.api.projects / git.repositories) — сбор ревью пропущен");
            return List.of();
        }

        // Опциональный кап окна бэкфилла: maxBackfillDays > 0 → не сканируем глубже now - N дней.
        // 0 (по умолчанию) — без ограничения, собираем всю историю от since прогона.
        LocalDateTime effectiveSince = updatedAfter;
        if (properties.maxBackfillDays() > 0) {
            LocalDateTime floor = LocalDateTime.now().minusDays(properties.maxBackfillDays());
            if (updatedAfter.isBefore(floor)) {
                effectiveSince = floor;
                log.info("GitLab: окно бэкфилла ограничено {} днями: since {} → {}",
                        properties.maxBackfillDays(), updatedAfter, effectiveSince);
            }
        }
        String updatedAfterIso = effectiveSince.atOffset(ZoneOffset.UTC).format(ISO_UTC);

        // public_email — опционально (по умолчанию off: пагинация по всем юзерам дорогая).
        Map<Long, String> publicEmailById = properties.fetchPublicEmails() ? fetchPublicEmails() : Map.of();

        log.info("GitLab: старт сбора ревью по {} проектам (updated_after={}, public_email={})",
                projects.size(), updatedAfterIso, properties.fetchPublicEmails() ? "вкл" : "username@домен");

        List<CollectedMergeRequest> result = new ArrayList<>();
        for (String project : projects) {
            try {
                collectProject(project, updatedAfterIso, publicEmailById, result);
            } catch (Exception e) {
                // Падение одного проекта не должно ронять остальные.
                log.error("GitLab: проект {} — сбор ревью упал: {}", project, e.getMessage(), e);
            }
        }
        log.info("GitLab: собрано {} MR с ревью по {} проектам", result.size(), projects.size());
        return result;
    }

    /* ----------------------------- users ----------------------------- */

    private Map<Long, String> fetchPublicEmails() {
        Map<Long, String> byId = new HashMap<>();
        int page = 1;
        int perPage = properties.pageSize();
        try {
            while (true) {
                int p = page;
                List<GitlabUserDto> users = rateLimiter.execute(
                        "GET /users page=" + p,
                        () -> http.getUsers(p, perPage, true));
                for (GitlabUserDto u : users) {
                    if (u.id() != null && u.publicEmail() != null && !u.publicEmail().isBlank()) {
                        byId.put(u.id(), u.publicEmail());
                    }
                }
                if (users.size() < perPage) break;
                page++;
            }
            log.info("GitLab: загружено {} public_email'ов", byId.size());
        } catch (HttpClientErrorException e) {
            // /users — best-effort. 403/401 (нет прав) и иной 4xx не валят сбор:
            // резолв пойдёт по username@домен (см. resolve()).
            log.warn("GitLab: /users недоступен ({}), public_email не загружен — резолв по username@{}",
                    e.getStatusCode(), properties.emailDomain());
        }
        return byId;
    }

    /* ----------------------------- MR + reviews ----------------------------- */

    private void collectProject(String project, String updatedAfterIso,
                                Map<Long, String> publicEmailById, List<CollectedMergeRequest> out) {
        // 1. Список MR — последовательно (дёшево: 1 запрос на страницу из pageSize MR).
        List<GitlabMrDto> allMrs = new ArrayList<>();
        int page = 1;
        int perPage = properties.pageSize();
        while (true) {
            int p = page;
            List<GitlabMrDto> mrs = rateLimiter.execute(
                    "GET /projects/" + project + "/merge_requests page=" + p,
                    () -> http.getMergeRequests(project, updatedAfterIso, p, perPage, "all", "all"));
            allMrs.addAll(mrs);
            if (mrs.size() < perPage) break;
            page++;
        }
        if (allMrs.isEmpty()) return;

        log.info("GitLab: {} — {} MR в списке, тяну approvals+notes (concurrency={})",
                project, allMrs.size(), properties.concurrency());

        // 2. Per-MR ревью (approvals + notes) — параллельно, bounded virtual threads.
        out.addAll(fetchReviewsConcurrently(project, allMrs, publicEmailById));
        log.info("GitLab: {} — всего {} MR обработано", project, allMrs.size());
    }

    /**
     * Сбор ревью по списку MR параллельно. {@code concurrency} ограничивает число одновременных
     * запросов к GitLab (Semaphore), virtual threads дёшевы. {@link #toCollected} и {@link #resolve}
     * не имеют общего изменяемого состояния — читают только immutable map/properties.
     *
     * <p><b>Deadline (P1-2).</b> На сбор одного проекта стоит верхняя граница
     * {@code gitlab.api.project-review-timeout} (по умолчанию 30m, {@code 0s} — без границы).
     * По её истечении недособранные MR отменяются, а уже собранные — сохраняются. Это страхует от
     * деградации GitLab, когда даже при bounded retry per-call десятки тысяч MR держали бы
     * advisory-лок часами. Потерянные MR (ошибки + отменённые) логируются <b>агрегатно</b>, а не
     * молча проглатываются по одному.</p>
     */
    private List<CollectedMergeRequest> fetchReviewsConcurrently(
            String project, List<GitlabMrDto> mrs, Map<Long, String> publicEmailById) {

        Semaphore gate = new Semaphore(Math.max(1, properties.concurrency()));
        AtomicInteger done = new AtomicInteger();
        int total = mrs.size();
        List<Future<CollectedMergeRequest>> futures = new ArrayList<>(total);
        List<CollectedMergeRequest> result = new ArrayList<>(total);

        Duration timeout = properties.projectReviewTimeout();
        boolean bounded = timeout != null && !timeout.isZero() && !timeout.isNegative();

        int failed = 0;
        int dropped = 0;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            for (GitlabMrDto mr : mrs) {
                futures.add(executor.submit(() -> {
                    gate.acquire();
                    try {
                        return toCollected(project, mr, publicEmailById);
                    } finally {
                        gate.release();
                        int n = done.incrementAndGet();
                        if (n % 200 == 0 || n == total) {
                            log.info("GitLab: {} — собрано ревью по {}/{} MR", project, n, total);
                        }
                    }
                }));
            }

            long deadlineNanos = System.nanoTime() + (bounded ? timeout.toNanos() : 0L);
            for (Future<CollectedMergeRequest> f : futures) {
                try {
                    // bounded: ждём не дольше остатка дедлайна; get(0) после дедлайна = неблокирующий
                    // опрос (уже-готовые добираем, ещё-бегущие → TimeoutException → отмена в finally).
                    CollectedMergeRequest c = bounded
                            ? f.get(Math.max(0L, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS)
                            : f.get();
                    if (c != null) result.add(c);
                } catch (TimeoutException te) {
                    // дедлайн исчерпан — этот MR не дождались; остальные так же отвалятся по 0-таймауту
                } catch (ExecutionException ee) {
                    failed++;
                    Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
                    log.debug("GitLab: {} — ревью одного MR не собрано: {}", project, cause.getMessage());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            // Отменяем всё, что не успело (дедлайн/прерывание), и гасим executor немедленно.
            for (Future<CollectedMergeRequest> f : futures) {
                if (!f.isDone() && f.cancel(true)) {
                    dropped++;
                }
            }
            executor.shutdownNow();
        }

        int lost = failed + dropped;
        if (lost > 0) {
            log.warn("GitLab: {} — НЕ собрано {} из {} MR (ошибки: {}, дедлайн/отмена: {}). "
                            + "Ревью-метрики по этим MR в текущем прогоне неполные — починятся следующим сбором.",
                    project, lost, total, failed, dropped);
        }
        return result;
    }

    private CollectedMergeRequest toCollected(String project, GitlabMrDto mr,
                                              Map<Long, String> publicEmailById) {
        Email authorEmail = resolve(mr.author(), publicEmailById);
        if (authorEmail == null) {
            log.debug("GitLab: MR {}!{} — не разрезолвлен автор, пропускаем", project, mr.iid());
            return null;
        }

        // Агрегируем участие по gitlab-user id: approve + число не-системных комментов.
        Map<Long, ReviewerAcc> reviewers = new LinkedHashMap<>();

        GitlabApprovalsDto approvals = fetchApprovals(project, mr.iid());
        if (approvals != null && approvals.approvedBy() != null) {
            for (GitlabApprovalsDto.ApprovedBy ab : approvals.approvedBy()) {
                if (ab.user() != null && ab.user().id() != null) {
                    reviewers.computeIfAbsent(ab.user().id(), k -> new ReviewerAcc(ab.user())).approved = true;
                }
            }
        }

        for (GitlabNoteDto note : fetchNotes(project, mr.iid())) {
            if (note.system() || note.author() == null || note.author().id() == null) continue;
            ReviewerAcc acc = reviewers.computeIfAbsent(note.author().id(), k -> new ReviewerAcc(note.author()));
            acc.commentCount++;
        }

        List<MrReview> reviews = new ArrayList<>();
        for (ReviewerAcc acc : reviewers.values()) {
            Email reviewerEmail = resolve(acc.user, publicEmailById);
            if (reviewerEmail == null || reviewerEmail.equals(authorEmail)) {
                continue; // не разрезолвлен или саморевью
            }
            reviews.add(new MrReview(reviewerEmail, acc.approved, acc.commentCount));
        }

        return new CollectedMergeRequest(
                mr.projectId() == null ? 0L : mr.projectId(),
                mr.iid(),
                authorEmail,
                mr.title(),
                mr.webUrl(),
                mr.state(),
                mr.createdAt() == null ? null : mr.createdAt().toLocalDateTime(),
                mr.mergedAt() == null ? null : mr.mergedAt().toLocalDateTime(),
                mr.targetBranch(),
                reviews);
    }

    private GitlabApprovalsDto fetchApprovals(String project, long iid) {
        try {
            return rateLimiter.execute(
                    "GET approvals " + project + "!" + iid,
                    () -> http.getApprovals(project, iid));
        } catch (HttpClientErrorException.NotFound e) {
            // approvals API недоступен на этом проекте/тарифе — считаем «без апрувов».
            return null;
        }
    }

    private List<GitlabNoteDto> fetchNotes(String project, long iid) {
        List<GitlabNoteDto> all = new ArrayList<>();
        int page = 1;
        int perPage = properties.pageSize();
        while (true) {
            int p = page;
            List<GitlabNoteDto> notes = rateLimiter.execute(
                    "GET notes " + project + "!" + iid + " page=" + p,
                    () -> http.getNotes(project, iid, p, perPage));
            all.addAll(notes);
            if (notes.size() < perPage) break;
            page++;
        }
        return all;
    }

    /* ----------------------------- identity ----------------------------- */

    /** public_email (если есть) иначе username@домен. {@code null} если не получилось. */
    private Email resolve(GitlabUserDto user, Map<Long, String> publicEmailById) {
        if (user == null) return null;
        String email = (user.publicEmail() != null && !user.publicEmail().isBlank())
                ? user.publicEmail()
                : publicEmailById.get(user.id());
        if ((email == null || email.isBlank()) && user.username() != null && !user.username().isBlank()) {
            email = user.username().toLowerCase(Locale.ROOT) + "@" + properties.emailDomain();
        }
        if (email == null || email.isBlank()) return null;
        try {
            return new Email(email);
        } catch (IllegalArgumentException e) {
            log.debug("GitLab: невалидный email '{}' для user {}", email, user.username());
            return null;
        }
    }

    /** Мутабельный аккумулятор участия одного ревьюера в одном MR. */
    private static final class ReviewerAcc {
        final GitlabUserDto user;
        boolean approved;
        int commentCount;

        ReviewerAcc(GitlabUserDto user) {
            this.user = user;
        }
    }
}
