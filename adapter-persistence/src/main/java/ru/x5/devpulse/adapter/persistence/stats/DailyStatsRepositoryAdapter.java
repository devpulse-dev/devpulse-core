package ru.x5.devpulse.adapter.persistence.stats;

import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.x5.devpulse.application.port.out.DailyStatsRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.stats.DailyAuthorStats;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Адаптер репозитория daily-агрегатов через native PostgreSQL SQL.
 *
 * <p>Запись — только через {@link #recomputeFromCommits} (атомарный пересчёт из
 * {@code commit_details} per-repo). Чтение — через JPA queries для индексированных
 * выборок по периоду/автору.</p>
 */
@Component
@Log4j2
@RequiredArgsConstructor
class DailyStatsRepositoryAdapter implements DailyStatsRepository {

    private final DailyAuthorStatsJpaRepository jpa;
    private final DailyStatsEntityMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Native SQL: пересборка агрегатов из commit_details для указанных email и периода.
     *
     * <p>Сначала удаляем затронутые строки, потом вставляем заново из commit_details
     * через {@code GROUP BY (email_lower, date, repo)}. Email в commit_details может быть в
     * исходном регистре — нормализуем через {@code LOWER()} чтобы ключ совпадал с upsert.</p>
     *
     * <p><b>Производительность:</b> WHERE использует {@code LOWER(email)} и
     * {@code CAST(commit_date AS DATE)}. Обычные индексы по {@code email} и {@code commit_date}
     * для таких выражений НЕ работают (не sargable). Functional composite indexes
     * {@code idx_commit_details_email_lower_date} и {@code idx_daily_stats_email_lower_date}
     * добавлены миграцией 019 — их форма ОБЯЗАНА совпадать с выражениями ниже. Если
     * меняешь {@code LOWER}/{@code CAST} здесь — синхронизируй миграцию.</p>
     */
    private static final String DELETE_SQL = """
            DELETE FROM daily_author_stats
            WHERE LOWER(email) = ANY (?)
              AND date BETWEEN ? AND ?
            """;

    // ВАЖНО про семантику колонок:
    //   commits       — ОБЩЕЕ число коммитов (включая мерджи); код в AuthorSummary вычитает мерджи
    //                   через nonMergeCommits() = max(0, commits − mergeCommits).
    //   merge_commits — подмножество (счётчик мерджей).
    // Если поменять commits на FILTER (is_merge=false), получится "20 commits, 36 merges" — что
    // даёт nonMergeCommits = 0 в Java-логике и сломанный дашборд (см. фикс этого бага).
    //
    // ВАЖНО про user_id:
    //   Берём из unified_user через LEFT JOIN + MAX(u.id). Миграция 020 добавила
    //   UNIQUE INDEX по LOWER(email) → для каждого LOWER(cd.email) ровно один matching u,
    //   значит MAX(u.id) == u.id (детерминированно). LEFT JOIN сохраняет cd rows без
    //   matching user'а (user_id = NULL) — это не падает на FK потому что nullable.
    //
    //   Альтернативы которые НЕ работают (история проб):
    //   1. `MAX(cd.user_id)` — недетерминированно при дубль-юзерах разного регистра
    //      (это была старая версия, ревью пункт #11).
    //   2. Correlated subquery `(SELECT u.id ... WHERE LOWER(u.email) = LOWER(cd.email) LIMIT 1)`
    //      — Postgres ругается "subquery uses ungrouped column": не распознаёт что выражение
    //      совпадает с group key.
    //   3. То же с alias `email` через outer SELECT — alias **shadows** колонку
    //      `unified_user.email`, subquery возвращает первый row по plan (все user_id одинаковые).
    //   4. То же с alias `email_lower` — Postgres standard SQL: aliases из outer SELECT
    //      **не видны** в subquery WHERE. Падает с "column does not exist".
    //
    //   Только JOIN + GROUP BY/MAX даёт корректный standard SQL.
    //
    //   Регрессионный тест: DailyStatsRepositoryAdapterIT.recomputeAssignsCorrectUserId.
    private static final String RECOMPUTE_SQL = """
            INSERT INTO daily_author_stats
                (email, date, repository_name, commits, merge_commits,
                 added_lines, deleted_lines, test_added_lines, last_updated, user_id)
            SELECT
                LOWER(cd.email)                                            AS email,
                CAST(cd.commit_date AS DATE)                               AS date,
                cd.repository_name                                         AS repo,
                COUNT(*)                                                   AS commits,
                COUNT(*) FILTER (WHERE cd.is_merge = true)                 AS merge_commits,
                COALESCE(SUM(cd.added_lines),     0)                       AS added_lines,
                COALESCE(SUM(cd.deleted_lines),   0)                       AS deleted_lines,
                COALESCE(SUM(cd.test_added_lines), 0)                      AS test_added_lines,
                NOW()                                                      AS last_updated,
                MAX(u.id)                                                  AS user_id
            FROM commit_details cd
            LEFT JOIN unified_user u ON LOWER(u.email) = LOWER(cd.email)
            WHERE LOWER(cd.email) = ANY (?)
              AND CAST(cd.commit_date AS DATE) BETWEEN ? AND ?
            GROUP BY LOWER(cd.email), CAST(cd.commit_date AS DATE), cd.repository_name
            """;

    @Override
    @Transactional
    public void recomputeFromCommits(Collection<Email> emails, Period period) {
        if (emails == null || emails.isEmpty()) return;
        // Email.value() — lowercase (инвариант VO). LOWER() в SQL ниже — defence in depth
        // на случай старых не-нормализованных строк в commit_details (миграция 020 их
        // привела к lowercase, но defensive LOWER страхует от регрессии).
        String[] values = emails.stream()
                .filter(e -> e != null && e.value() != null)
                .map(Email::value)
                .distinct()
                .toArray(String[]::new);
        if (values.length == 0) return;

        int deleted = jdbcTemplate.update(DELETE_SQL, ps -> {
            ps.setArray(1, ps.getConnection().createArrayOf("text", values));
            ps.setObject(2, period.from());
            ps.setObject(3, period.to());
        });
        int inserted = jdbcTemplate.update(RECOMPUTE_SQL, ps -> {
            ps.setArray(1, ps.getConnection().createArrayOf("text", values));
            ps.setObject(2, period.from());
            ps.setObject(3, period.to());
        });
        log.info("Пересобрали daily_stats для {} авторов в [{}..{}]: -{} +{}",
                values.length, period.from(), period.to(), deleted, inserted);
    }

    @Override
    public List<DailyAuthorStats> findByPeriod(Period period) {
        return jpa.findByPeriod(period.from(), period.to()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<DailyAuthorStats> findByAuthorAndPeriod(Email email, Period period) {
        return jpa.findByAuthorAndPeriod(email.value(), period.from(), period.to()).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
