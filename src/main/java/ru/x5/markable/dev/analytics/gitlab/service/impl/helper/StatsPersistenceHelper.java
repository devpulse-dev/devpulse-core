package ru.x5.markable.dev.analytics.gitlab.service.impl.helper;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import ru.x5.markable.dev.analytics.gitlab.model.AuthorAggregate;
import ru.x5.markable.dev.analytics.gitlab.service.UnifiedUserService;

/**
 * Сохраняет ежедневную статистику в БД одним bulk upsert.
 *
 * <p>Использует PostgreSQL {@code INSERT ... ON CONFLICT (email, date, repository_name)
 * DO UPDATE} — атомарный upsert без race conditions, без чувствительности к регистру
 * (предварительно нормализуем email через {@code toLowerCase}), и без N+1 SELECT'ов.</p>
 *
 * <p>В одной транзакции:
 * <ol>
 *   <li>1 batch find-or-create users — карта email → userId</li>
 *   <li>Дедупликация записей в памяти по (email_lower, date)</li>
 *   <li>1 native batch upsert через {@link JdbcTemplate#batchUpdate}</li>
 * </ol>
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class StatsPersistenceHelper {

    private final JdbcTemplate jdbcTemplate;
    private final UnifiedUserService unifiedUserService;

    private static final String UPSERT_SQL = """
            INSERT INTO daily_author_stats
                (email, date, repository_name, merge_commits, commits,
                 added_lines, deleted_lines, test_added_lines, last_updated, user_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (email, date, repository_name) DO UPDATE SET
                merge_commits    = EXCLUDED.merge_commits,
                commits          = EXCLUDED.commits,
                added_lines      = EXCLUDED.added_lines,
                deleted_lines    = EXCLUDED.deleted_lines,
                test_added_lines = EXCLUDED.test_added_lines,
                last_updated     = EXCLUDED.last_updated,
                user_id          = EXCLUDED.user_id
            """;

    @Transactional
    public void saveDailyStatsForRepo(Map<LocalDate, Map<String, AuthorAggregate>> dailyStats, String repoName) {
        if (dailyStats == null || dailyStats.isEmpty()) return;

        // 1. Собираем уникальные нормализованные email'ы
        Set<String> emails = new HashSet<>();
        for (Map<String, AuthorAggregate> dayStats : dailyStats.values()) {
            for (String email : dayStats.keySet()) {
                if (email != null) emails.add(email.toLowerCase());
            }
        }
        Map<String, Long> userIdByEmail = unifiedUserService.findOrCreateAllByEmails(emails);

        // 2. Дедуплицируем по (email_lower, date) — на случай если в map были разные регистры
        Map<String, Row> rows = new java.util.HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        for (Map.Entry<LocalDate, Map<String, AuthorAggregate>> dayEntry : dailyStats.entrySet()) {
            LocalDate date = dayEntry.getKey();
            for (Map.Entry<String, AuthorAggregate> e : dayEntry.getValue().entrySet()) {
                String email = e.getKey().toLowerCase();
                AuthorAggregate stat = e.getValue();
                Long userId = userIdByEmail.get(email);

                rows.merge(email + '|' + date,
                        new Row(email, date, stat, userId, now),
                        Row::merge);
            }
        }

        if (rows.isEmpty()) return;

        // 3. Один native batch upsert
        List<Row> rowList = new ArrayList<>(rows.values());
        jdbcTemplate.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Row r = rowList.get(i);
                ps.setString(1, r.email);
                ps.setObject(2, r.date);
                ps.setString(3, repoName);
                ps.setLong(4, r.stat.mergeCommits());
                ps.setLong(5, r.stat.commits());
                ps.setLong(6, r.stat.added());
                ps.setLong(7, r.stat.deleted());
                ps.setLong(8, r.stat.testAdded());
                ps.setTimestamp(9, Timestamp.valueOf(r.now));
                if (r.userId != null) {
                    ps.setLong(10, r.userId);
                } else {
                    ps.setNull(10, Types.BIGINT);
                }
            }

            @Override
            public int getBatchSize() {
                return rowList.size();
            }
        });

        log.info("Upserted {} daily stats records for repo {}", rowList.size(), repoName);
    }

    private record Row(String email, LocalDate date, AuthorAggregate stat, Long userId, LocalDateTime now) {
        Row merge(Row other) {
            // Если две записи попали в один ключ (разный регистр email в исходных данных) — складываем
            return new Row(this.email, this.date, this.stat.merge(other.stat), this.userId, this.now);
        }
    }
}
