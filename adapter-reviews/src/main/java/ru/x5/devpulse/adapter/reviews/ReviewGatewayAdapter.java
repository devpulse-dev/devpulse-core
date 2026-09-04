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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
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

    /**
     * Верхний потолок числа страниц пагинации (MR / notes / users). Защита от зациклившегося
     * или аномального API: без него {@code while(size==perPage)} мог бы крутиться бесконечно.
     * При {@code pageSize=100} это 100k элементов на одну пагинацию — заведомо выше реального
     * объёма проекта; достижение капа логируется WARN'ом (данные усечены, доберёт следующий сбор).
     */
    private static final int MAX_PAGES = 1000;

    /**
     * Множитель размера волны fan-out: за раз обрабатываем {@code concurrency × WAVE_MULTIPLIER} MR,
     * а не весь проект сразу. Ограничивает число одновременных futures/VT в heap (крупный проект на
     * 10^5 MR иначе дал бы heap-спайк). Barrier между волнами амортизируется множителем > 1.
     */
    private static final int WAVE_MULTIPLIER = 4;

    /**
     * Сколько собранных MR накапливать перед отдачей на запись. Раньше батч был равен целому
     * проекту: на xrg-core это ~20k MR и полчаса работы, которые жили только в heap — рестарт
     * или сбой на 29-й минуте терял всё, курсор не двигался, и следующий прогон начинал заново
     * (бэкфилл не сходился в принципе). Теперь результат закрепляется по ходу.
     *
     * <p>Значение совпадает с размером чанка upsert'а в {@code ReviewWriteRepositoryAdapter}:
     * больше — растёт объём потерь при сбое, меньше — чаще короткие транзакции без выигрыша.
     * Сброс происходит на границе волны, поэтому фактический размер батча — первое кратное
     * {@code concurrency × WAVE_MULTIPLIER}, превысившее порог.</p>
     */
    private static final int FLUSH_THRESHOLD = 500;

    @Override
    public void streamMergeRequests(LocalDateTime updatedAfter,
                                    BooleanSupplier cancelled,
                                    Consumer<List<CollectedMergeRequest>> batchHandler) {
        List<String> projects = GitlabProjectPaths.resolve(properties, gitRepos);
        if (projects.isEmpty()) {
            log.warn("GitLab: не настроены проекты (gitlab.api.projects / git.repositories) — сбор ревью пропущен");
            return;
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

        int grandTotal = 0;
        for (String project : projects) {
            // Checkpoint отмены: между проектами (симметрично git между репо). Прекращаем опрашивать
            // новые проекты; собранное уже отдано на запись. Оркестратор зафиксирует CANCELLED.
            if (cancelled.getAsBoolean()) {
                log.info("GitLab: отмена — прекращаю сбор ревью перед проектом {}", project);
                break;
            }
            try {
                // Стриминг чанками ПО ХОДУ проекта, а не одним батчем в конце: собранное
                // закрепляется в БД каждые ~FLUSH_THRESHOLD MR, поэтому сбой на середине
                // крупного проекта стоит минут работы, а не всего проекта.
                grandTotal += collectProject(project, updatedAfterIso, publicEmailById, batchHandler);
            } catch (Exception e) {
                // Падение одного проекта не должно ронять остальные.
                log.error("GitLab: проект {} — сбор ревью упал: {}", project, e.getMessage(), e);
            }
        }
        log.info("GitLab: собрано {} MR с ревью по {} проектам", grandTotal, projects.size());
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
                if (page >= MAX_PAGES) {
                    log.warn("GitLab: потолок пагинации /users ({} стр.), public_email усечён", MAX_PAGES);
                    break;
                }
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

    /** @return сколько MR собрано и отдано на запись (сумма по всем чанкам проекта). */
    private int collectProject(String project, String updatedAfterIso,
                               Map<Long, String> publicEmailById,
                               Consumer<List<CollectedMergeRequest>> batchHandler) {
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
            if (p >= MAX_PAGES) {
                log.warn("GitLab: {} — достигнут потолок пагинации MR ({} стр. × {}), список усечён — "
                        + "часть MR не собрана этим прогоном (доберёт следующий)", project, MAX_PAGES, perPage);
                break;
            }
            page++;
        }
        if (allMrs.isEmpty()) return 0;

        // Сколько MR пойдут без запроса notes (user_notes_count=0) — видно эффект экономии прямо
        // в логе прогона: это чистое вычитание из бюджета RPS, самого дефицитного ресурса фазы.
        long withoutNotes = allMrs.stream().filter(GitlabMrDto::hasNoUserNotes).count();
        log.info("GitLab: {} — {} MR в списке, тяну approvals+notes (concurrency={}, "
                        + "без комментариев: {} — запрос notes пропускается)",
                project, allMrs.size(), properties.concurrency(), withoutNotes);

        // 2. Per-MR ревью (approvals + notes) — параллельно, bounded virtual threads.
        //    Собранное отдаётся на запись чанками по ходу, а не возвращается одним списком.
        int collected = fetchReviewsConcurrently(project, allMrs, publicEmailById, batchHandler);
        log.info("GitLab: {} — всего {} MR обработано", project, allMrs.size());
        return collected;
    }

    /**
     * Сбор ревью по списку MR параллельно. {@code concurrency} ограничивает число одновременных
     * запросов к GitLab (Semaphore), virtual threads дёшевы. {@link #toCollected} и {@link #resolve}
     * не имеют общего изменяемого состояния — читают только immutable map/properties.
     *
     * <p><b>Волнами (heap-cap).</b> MR обрабатываются партиями по {@code concurrency ×
     * WAVE_MULTIPLIER}, а не сабмитятся все сразу — иначе на крупном проекте (10^5 MR) в heap
     * одновременно висели бы 10^5 futures/VT (спайк в сотни МБ). {@code concurrency} по-прежнему
     * ограничивает одновременный HTTP через Semaphore.</p>
     *
     * <p><b>Deadline (P1-2).</b> На сбор одного проекта стоит верхняя граница
     * {@code gitlab.api.project-review-timeout} (deployed-дефолт 1h, {@code 0s} — без границы).
     * По её истечении недособранные MR отменяются, а уже собранные — сохраняются. Это страхует от
     * деградации GitLab, когда даже при bounded retry per-call десятки тысяч MR держали бы
     * advisory-лок часами. Потерянные MR (ошибки + отменённые) логируются <b>агрегатно</b>, а не
     * молча проглатываются по одному.</p>
     *
     * <p><b>Сброс чанками.</b> Собранное не копится до конца проекта, а уходит в
     * {@code batchHandler} на границе волны, как только накопится {@link #FLUSH_THRESHOLD}.
     * Граница волны выбрана точкой сброса потому, что там уже нет живых futures — чанк
     * консистентен, и его запись не конкурирует со сбором. Дедлайн и отмена не теряют остаток:
     * финальный сброс идёт после цикла.</p>
     *
     * @return сколько MR фактически отдано на запись
     */
    private int fetchReviewsConcurrently(
            String project, List<GitlabMrDto> mrs, Map<Long, String> publicEmailById,
            Consumer<List<CollectedMergeRequest>> batchHandler) {

        int concurrency = Math.max(1, properties.concurrency());
        int waveSize = concurrency * WAVE_MULTIPLIER;
        Semaphore gate = new Semaphore(concurrency);
        int total = mrs.size();
        // Буфер под порог + одну волну (сброс на границе волны может его перешагнуть), а не под
        // весь проект: на 20k MR прежний ArrayList(total) держал в heap весь корпус до конца.
        List<CollectedMergeRequest> buffer = new ArrayList<>(Math.min(total, FLUSH_THRESHOLD + waveSize));
        int flushed = 0;

        Duration timeout = properties.projectReviewTimeout();
        boolean bounded = timeout != null && !timeout.isZero() && !timeout.isNegative();
        long deadlineNanos = System.nanoTime() + (bounded ? timeout.toNanos() : 0L);

        int failed = 0;
        int dropped = 0;
        int done = 0;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            // Волнами по waveSize: одновременно в heap максимум waveSize futures/VT, а не весь проект.
            // Крупный проект (10^5 MR) иначе дал бы heap-спайк в сотни МБ на eager-submit всех задач.
            waves:
            for (int start = 0; start < total; start += waveSize) {
                if (bounded && System.nanoTime() >= deadlineNanos) {
                    dropped += total - start; // остаток волн не начат — дедлайн проекта исчерпан
                    break;
                }
                int end = Math.min(start + waveSize, total);
                List<Future<CollectedMergeRequest>> futures = new ArrayList<>(end - start);
                for (GitlabMrDto mr : mrs.subList(start, end)) {
                    futures.add(executor.submit(() -> {
                        gate.acquire();
                        try {
                            return toCollected(project, mr, publicEmailById);
                        } finally {
                            gate.release();
                        }
                    }));
                }
                for (Future<CollectedMergeRequest> f : futures) {
                    try {
                        CollectedMergeRequest c = bounded
                                ? f.get(Math.max(0L, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS)
                                : f.get();
                        if (c != null) buffer.add(c);
                    } catch (TimeoutException te) {
                        // дедлайн — этот и последующие в волне отвалятся по 0-таймауту
                    } catch (ExecutionException ee) {
                        failed++;
                        Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
                        log.debug("GitLab: {} — ревью одного MR не собрано: {}", project, cause.getMessage());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        for (Future<CollectedMergeRequest> g : futures) {
                            if (!g.isDone() && g.cancel(true)) dropped++;
                        }
                        break waves;
                    }
                    done++;
                    if (done % 200 == 0 || done == total) {
                        log.info("GitLab: {} — собрано ревью по {}/{} MR", project, done, total);
                    }
                }
                // Отменяем недособранное текущей волны (дедлайн) перед следующей.
                for (Future<CollectedMergeRequest> f : futures) {
                    if (!f.isDone() && f.cancel(true)) dropped++;
                }
                // Граница волны — здесь нет живых futures, чанк консистентен.
                if (buffer.size() >= FLUSH_THRESHOLD) {
                    flushed += flush(project, buffer, batchHandler, done, total);
                }
            }
        } finally {
            executor.shutdownNow();
        }

        // Остаток — в том числе после дедлайна или отмены: то, что успели собрать, должно быть
        // записано. Вне finally сознательно: если сброс внутри цикла упал (ошибка записи), не
        // маскируем исходное исключение повторной попыткой на том же handler'е.
        flushed += flush(project, buffer, batchHandler, done, total);

        int lost = failed + dropped;
        if (lost > 0) {
            log.warn("GitLab: {} — НЕ собрано {} из {} MR (ошибки: {}, дедлайн/отмена: {}). "
                            + "Ревью-метрики по этим MR в текущем прогоне неполные — починятся следующим сбором.",
                    project, lost, total, failed, dropped);
        }
        return flushed;
    }

    /**
     * Отдаёт накопленный чанк на запись и очищает буфер. Пустой буфер — no-op (обработчик не
     * дёргается вхолостую).
     *
     * @return сколько MR отдано
     */
    private int flush(String project, List<CollectedMergeRequest> buffer,
                      Consumer<List<CollectedMergeRequest>> batchHandler, int done, int total) {
        if (buffer.isEmpty()) return 0;
        int size = buffer.size();
        // Копия: обработчик получает свой список, а буфер переиспользуется дальше по волнам.
        batchHandler.accept(new ArrayList<>(buffer));
        buffer.clear();
        log.info("GitLab: {} — записано {} MR (прогресс {}/{})", project, size, done, total);
        return size;
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

        // MR без пользовательских комментариев — не тратим на него запрос: весь бюджет RPS уходит
        // именно на per-MR вызовы. GitLab считает user_notes_count по тому же критерию, по которому
        // мы фильтруем ниже (system=false), поэтому пропуск не теряет ни одного ревьюера. Поля нет
        // в ответе (null) → идём за notes, как раньше.
        if (!mr.hasNoUserNotes()) {
            for (GitlabNoteDto note : fetchNotes(project, mr.iid())) {
                if (note.system() || note.author() == null || note.author().id() == null) continue;
                ReviewerAcc acc = reviewers.computeIfAbsent(note.author().id(), k -> new ReviewerAcc(note.author()));
                acc.commentCount++;
            }
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
            if (p >= MAX_PAGES) {
                log.warn("GitLab: {}!{} — потолок пагинации notes ({} стр.), усечено", project, iid, MAX_PAGES);
                break;
            }
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
