package ru.x5.markable.dev.analytics.adapter.persistence.stats;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.x5.markable.dev.analytics.application.port.out.DailyStatsRepository;
import ru.x5.markable.dev.analytics.application.port.out.UnifiedUserRepository;
import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.model.git.RepoName;
import ru.x5.markable.dev.analytics.domain.model.stats.DailyAuthorStats;
import ru.x5.markable.dev.analytics.domain.model.user.Email;

/**
 * Адаптер репозитория daily-агрегатов с native PostgreSQL UPSERT.
 *
 * <p>Bulk upsert через {@code INSERT ... ON CONFLICT (email, date, repository_name) DO UPDATE}
 * — одной операцией заменяет связку "SELECT существующих + JPA merge" из старого кода.</p>
 */
@Component
@Log4j2
@RequiredArgsConstructor
class DailyStatsRepositoryAdapter implements DailyStatsRepository {

    private final DailyAuthorStatsJpaRepository jpa;
    private final DailyStatsEntityMapper mapper;
    private final UnifiedUserRepository unifiedUsers;
    private final JdbcTemplate jdbcTemplate;

    private static final String UPSERT_SQL = """
            INSERT INTO daily_author_stats
                (email, date, repository_name, commits, merge_commits,
                 added_lines, deleted_lines, test_added_lines, last_updated, user_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (email, date, repository_name) DO UPDATE SET
                commits          = EXCLUDED.commits,
                merge_commits    = EXCLUDED.merge_commits,
                added_lines      = EXCLUDED.added_lines,
                deleted_lines    = EXCLUDED.deleted_lines,
                test_added_lines = EXCLUDED.test_added_lines,
                last_updated     = EXCLUDED.last_updated,
                user_id          = COALESCE(EXCLUDED.user_id, daily_author_stats.user_id)
            """;

    @Override
    @Transactional
    public void upsertAll(Collection<DailyAuthorStats> stats) {
        if (stats == null || stats.isEmpty()) return;

        // 1 batch find-or-create — все user_id одной операцией
        Set<Email> emails = new HashSet<>();
        stats.forEach(s -> emails.add(s.authorEmail()));
        Map<Email, Long> userByEmail = unifiedUsers.findOrCreateAll(emails);

        LocalDateTime now = LocalDateTime.now();
        List<DailyAuthorStats> list = new ArrayList<>(stats);

        int[] counts = jdbcTemplate.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                DailyAuthorStats s = list.get(i);
                Long userId = userByEmail.get(s.authorEmail());
                ps.setString(1, s.authorEmail().value());
                ps.setObject(2, s.date());
                ps.setString(3, s.repo().value());
                ps.setLong(4, s.commits());
                ps.setLong(5, s.mergeCommits());
                ps.setLong(6, s.addedLines());
                ps.setLong(7, s.deletedLines());
                ps.setLong(8, s.testAddedLines());
                ps.setTimestamp(9, Timestamp.valueOf(s.lastUpdated() != null ? s.lastUpdated() : now));
                if (userId == null) {
                    ps.setNull(10, Types.BIGINT);
                } else {
                    ps.setLong(10, userId);
                }
            }

            @Override
            public int getBatchSize() {
                return list.size();
            }
        });
        log.debug("Upserted {} daily stats rows", counts.length);
    }

    /**
     * Native SQL: пересборка агрегатов из commit_details для указанных email и периода.
     *
     * <p>Сначала удаляем затронутые строки, потом вставляем заново из commit_details
     * через {@code GROUP BY (email_lower, date, repo)}. Email в commit_details может быть в
     * исходном регистре — нормализуем через {@code LOWER()} чтобы ключ совпадал с upsert.</p>
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
                MAX(cd.user_id)                                            AS user_id
            FROM commit_details cd
            WHERE LOWER(cd.email) = ANY (?)
              AND CAST(cd.commit_date AS DATE) BETWEEN ? AND ?
            GROUP BY LOWER(cd.email), CAST(cd.commit_date AS DATE), cd.repository_name
            """;

    @Override
    @Transactional
    public void recomputeFromCommits(Collection<Email> emails, Period period) {
        if (emails == null || emails.isEmpty()) return;
        String[] normalized = emails.stream()
                .filter(e -> e != null && e.value() != null)
                .map(e -> e.value().toLowerCase())
                .distinct()
                .toArray(String[]::new);
        if (normalized.length == 0) return;

        int deleted = jdbcTemplate.update(DELETE_SQL, ps -> {
            ps.setArray(1, ps.getConnection().createArrayOf("text", normalized));
            ps.setObject(2, period.from());
            ps.setObject(3, period.to());
        });
        int inserted = jdbcTemplate.update(RECOMPUTE_SQL, ps -> {
            ps.setArray(1, ps.getConnection().createArrayOf("text", normalized));
            ps.setObject(2, period.from());
            ps.setObject(3, period.to());
        });
        log.info("Пересобрали daily_stats для {} авторов в [{}..{}]: -{} +{}",
                normalized.length, period.from(), period.to(), deleted, inserted);
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

    @Override
    public List<DailyAuthorStats> findByRepoAndPeriod(RepoName repo, Period period) {
        return jpa.findByRepoAndPeriod(repo.value(), period.from(), period.to()).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
