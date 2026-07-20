package ru.x5.devpulse.adapter.gitlab;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация GitLab-адаптера для сбора ревью-метрик.
 *
 * @param baseUrl       базовый URL GitLab API, напр. {@code https://scm.x5.ru/api/v4}
 * @param token         PAT с правами {@code read_api} (передаётся в заголовке {@code PRIVATE-TOKEN})
 * @param projects      пути проектов GitLab ({@code namespace/repo}); если пусто — деривятся
 *                      из {@code git.repositories} (clone-URL → path без схемы/.git)
 * @param emailDomain   домен для резолва {@code username → username@домен}
 * @param fetchPublicEmails тянуть ли {@code GET /users} ради {@code public_email}. По умолчанию
 *                      {@code false}: пагинация по ВСЕМ юзерам GitLab дорогая (минуты), а
 *                      {@code username@домен} для scm.x5.ru совпадает с email. Включай, только
 *                      если username ≠ local-part email у части людей.
 * @param maxBackfillDays максимальная глубина сбора назад от now (дней). {@code 0} (по умолчанию) —
 *                      без ограничения: собирать всю историю от {@code since} прогона. Поставь
 *                      &gt; 0, если нужно ограничить первый прогон (например на медленном GitLab).
 * @param concurrency   сколько MR обрабатывать параллельно (approvals+notes на virtual threads).
 *                      Главный рычаг скорости: per-MR вызовы — bottleneck. По умолчанию 8.
 * @param requestDelayMs минимальная пауза между запросами (троттлинг, per-thread)
 * @param maxRetries    повторов на 429/5xx
 * @param retryBackoffMs стартовый backoff (экспоненциальный)
 * @param pageSize      размер страницы пагинации
 * @param insecureSsl   trust-all SSL (только dev; scm.x5.ru за внутренним CA)
 * @param connectTimeout таймаут соединения
 * @param readTimeout   таймаут чтения
 * @param projectReviewTimeout верхняя граница на сбор ревью одного проекта (fan-out по MR).
 *                      Защита от деградации GitLab: даже при bounded retry per-call десятки тысяч
 *                      MR могут тянуться часами под advisory-локом. По истечении недособранные MR
 *                      отменяются, прогон продолжается (с агрегатным WARN о потерянных). Default
 *                      {@code 30m}; {@code 0s} — без ограничения (старое поведение).
 */
@ConfigurationProperties(prefix = "gitlab.api")
public record GitlabProperties(
        String baseUrl,
        String token,
        List<String> projects,
        String emailDomain,
        boolean fetchPublicEmails,
        int maxBackfillDays,
        int concurrency,
        long requestDelayMs,
        int maxRetries,
        long retryBackoffMs,
        int pageSize,
        boolean insecureSsl,
        Duration connectTimeout,
        Duration readTimeout,
        Duration projectReviewTimeout
) {
    public GitlabProperties {
        projects = projects == null ? List.of() : List.copyOf(projects);
        if (emailDomain == null || emailDomain.isBlank()) emailDomain = "x5.ru";
        if (maxBackfillDays < 0) maxBackfillDays = 0; // 0 = без ограничения
        if (concurrency <= 0) concurrency = 8;
        if (requestDelayMs <= 0) requestDelayMs = 200;
        if (maxRetries <= 0) maxRetries = 5;
        if (retryBackoffMs <= 0) retryBackoffMs = 2_000;
        if (pageSize <= 0) pageSize = 100;
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(5);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(30);
        // null → дефолтная граница 30m. Явный 0s/отрицательное → без границы (проверяется в адаптере).
        if (projectReviewTimeout == null) projectReviewTimeout = Duration.ofMinutes(30);
    }
}
