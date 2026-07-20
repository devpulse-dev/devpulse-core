package ru.x5.devpulse.adapter.persistence.stats;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.x5.devpulse.application.port.out.DailyStatsRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.cohort.MonthlyAuthorActivity;
import ru.x5.devpulse.domain.model.stats.AuthorSummary;
import ru.x5.devpulse.domain.model.stats.DailyAuthorStats;
import ru.x5.devpulse.domain.model.stats.WeeklyAuthorActivity;
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

    /**
     * Помесячный агрегат активности по автору за окно (GROUP BY в БД — не тянем сырой daily в heap).
     *
     * <p>Email в {@code daily_author_stats} уже lowercase (миграция 020); {@code LOWER()} —
     * defence in depth. Team-фильтр через подзапрос на {@code unified_user} (членство по email);
     * {@code CAST(? AS text) IS NULL} → null-team = без фильтра. Месяц — {@code to_char(date,'YYYY-MM')},
     * парсится в {@link YearMonth} (ISO {@code uuuu-MM}).</p>
     */
    private static final String MONTHLY_SQL = """
            SELECT LOWER(s.email)             AS email,
                   to_char(s.date, 'YYYY-MM') AS ym,
                   SUM(s.commits)             AS commits,
                   SUM(s.merge_commits)       AS merge_commits,
                   SUM(s.added_lines)         AS added_lines,
                   SUM(s.deleted_lines)       AS deleted_lines
              FROM daily_author_stats s
             WHERE s.date BETWEEN ? AND ?
               AND (CAST(? AS text) IS NULL
                    OR LOWER(s.email) IN (SELECT u.email FROM unified_user u WHERE u.team = ?))
             GROUP BY LOWER(s.email), to_char(s.date, 'YYYY-MM')
            """;

    @Override
    public List<MonthlyAuthorActivity> monthlyAuthorActivity(LocalDate from, LocalDate to, String team) {
        return jdbcTemplate.query(MONTHLY_SQL,
                ps -> {
                    ps.setObject(1, from);
                    ps.setObject(2, to);
                    ps.setString(3, team); // null → CAST(null AS text) IS NULL → фильтр отключён
                    ps.setString(4, team);
                },
                (rs, rowNum) -> new MonthlyAuthorActivity(
                        new Email(rs.getString("email")),
                        YearMonth.parse(rs.getString("ym")),
                        rs.getLong("commits"),
                        rs.getLong("merge_commits"),
                        rs.getLong("added_lines"),
                        rs.getLong("deleted_lines")));
    }

    /**
     * Агрегат по автору за период: одна строка на автора. Свёртка {@code (email, date, repo)}-строк
     * до {@code (email)} делается в БД (GROUP BY), heap не растёт с длиной периода.
     *
     * <p>{@code LOWER(email)} — email в {@code daily_author_stats} уже lowercase (миграция 020),
     * {@code LOWER()} страхует от старых строк и совпадает по форме с functional-индексом
     * {@code idx_daily_stats_email_lower_date(LOWER(email), date)} — фильтр по {@code date} +
     * группировка по {@code LOWER(email)} им покрываются.</p>
     */
    private static final String AGGREGATE_AUTHORS_SQL = """
            SELECT LOWER(email)                       AS email,
                   COALESCE(SUM(commits),          0) AS commits,
                   COALESCE(SUM(merge_commits),    0) AS merge_commits,
                   COALESCE(SUM(added_lines),      0) AS added_lines,
                   COALESCE(SUM(deleted_lines),    0) AS deleted_lines,
                   COALESCE(SUM(test_added_lines), 0) AS test_added_lines
              FROM daily_author_stats
             WHERE date BETWEEN ? AND ?
             GROUP BY LOWER(email)
            """;

    @Override
    public List<AuthorSummary> aggregateAuthorsByPeriod(Period period) {
        return jdbcTemplate.query(AGGREGATE_AUTHORS_SQL,
                ps -> {
                    ps.setObject(1, period.from());
                    ps.setObject(2, period.to());
                },
                (rs, rowNum) -> new AuthorSummary(
                        new Email(rs.getString("email")),
                        null, null,
                        rs.getLong("commits"),
                        rs.getLong("merge_commits"),
                        rs.getLong("added_lines"),
                        rs.getLong("deleted_lines"),
                        rs.getLong("test_added_lines"),
                        null, null, false));
    }

    @Override
    public List<DailyAuthorStats> findByPeriod(Period period) {
        return jpa.findByPeriod(period.from(), period.to()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<DailyAuthorStats> findByPeriod(Period period,
                                               java.util.Optional<Email> author,
                                               java.util.Optional<String> team) {
        // Email.value() — уже lowercase (инвариант VO); JPQL lower(s.email) — defence in depth.
        String email = author.map(Email::value).orElse(null);
        String teamFilter = team.filter(t -> !t.isBlank()).orElse(null);
        return jpa.findByPeriodFiltered(period.from(), period.to(), email, teamFilter).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<DailyAuthorStats> findByAuthorAndPeriod(Email email, Period period) {
        return jpa.findByAuthorAndPeriod(email.value(), period.from(), period.to()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    /**
     * Понедельный агрегат по автору: GROUP BY в БД по {@code (LOWER(email), ISO-год, ISO-неделя)}.
     * Не поднимает все daily-строки периода в heap. {@code EXTRACT(ISOYEAR/WEEK)} — ISO-8601 неделя
     * (1..53) и её week-based-год, согласовано с {@code StatsSummarizer.weekStart}.
     */
    private static final String WEEKLY_ACTIVITY_SQL = """
            SELECT LOWER(email)                          AS email,
                   EXTRACT(ISOYEAR FROM date)::int       AS iso_year,
                   EXTRACT(WEEK    FROM date)::int       AS iso_week,
                   COALESCE(SUM(commits),          0)    AS commits,
                   COALESCE(SUM(merge_commits),    0)    AS merge_commits,
                   COALESCE(SUM(added_lines),      0)    AS added_lines,
                   COALESCE(SUM(deleted_lines),    0)    AS deleted_lines,
                   COALESCE(SUM(test_added_lines), 0)    AS test_added_lines
              FROM daily_author_stats
             WHERE date BETWEEN ? AND ?
             GROUP BY LOWER(email), EXTRACT(ISOYEAR FROM date), EXTRACT(WEEK FROM date)
            """;

    @Override
    public List<WeeklyAuthorActivity> weeklyAuthorActivity(Period period) {
        return jdbcTemplate.query(WEEKLY_ACTIVITY_SQL,
                ps -> {
                    ps.setObject(1, period.from());
                    ps.setObject(2, period.to());
                },
                (rs, rowNum) -> new WeeklyAuthorActivity(
                        new Email(rs.getString("email")),
                        rs.getInt("iso_year"),
                        rs.getInt("iso_week"),
                        rs.getLong("commits"),
                        rs.getLong("merge_commits"),
                        rs.getLong("added_lines"),
                        rs.getLong("deleted_lines"),
                        rs.getLong("test_added_lines")));
    }
}
