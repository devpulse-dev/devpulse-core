package ru.x5.devpulse.adapter.persistence.commit;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.x5.devpulse.application.port.out.CommitRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.git.CommitHash;
import ru.x5.devpulse.domain.model.git.RepoName;
import ru.x5.devpulse.domain.model.stats.HourlyBucket;
import ru.x5.devpulse.domain.model.user.Email;

@Component
@Log4j2
@RequiredArgsConstructor
class CommitRepositoryAdapter implements CommitRepository {

    /**
     * Native INSERT в обход JPA. {@code commit_details} пишется append-only, сгенерированный id
     * обратно не нужен. JPA {@code saveAll} с {@code GenerationType.IDENTITY} ломает Hibernate
     * JDBC batching (insert по одному round-trip'у на строку) — поэтому пишем через
     * {@link JdbcTemplate#batchUpdate}. См. P0-1 / ADR-11.
     */
    private static final String INSERT_SQL = """
            INSERT INTO commit_details
                (commit_hash, email, commit_date, hour, is_merge, task_number, commit_message,
                 added_lines, deleted_lines, test_added_lines, repository_name, collected_at,
                 user_id, kaiten_card_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /** Размер под-батча для {@code batchUpdate}; совпадает с batch-size стрима коммитов. */
    private static final int INSERT_BATCH_SIZE = 500;

    private final CommitDetailsJpaRepository jpa;
    private final CommitEntityMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Set<CommitHash> findExistingHashes(Collection<CommitHash> hashes) {
        if (hashes == null || hashes.isEmpty()) return Set.of();
        List<String> values = hashes.stream().map(CommitHash::value).toList();
        Set<CommitHash> result = new HashSet<>();
        for (String v : jpa.findExistingHashes(values)) {
            result.add(new CommitHash(v));
        }
        return result;
    }

    @Override
    @Transactional
    public void saveAll(Collection<Commit> commits, Map<Email, Long> userByEmail) {
        if (commits == null || commits.isEmpty()) return;

        Map<Email, Long> users = userByEmail == null ? Map.of() : userByEmail;
        LocalDateTime collectedAt = LocalDateTime.now();

        jdbcTemplate.batchUpdate(INSERT_SQL, commits, INSERT_BATCH_SIZE, (ps, c) -> {
            Email email = c.authorEmail();
            ps.setString(1, c.hash().value());
            ps.setString(2, email == null ? null : email.value());
            ps.setObject(3, c.commitDate());
            ps.setInt(4, c.hour());
            ps.setBoolean(5, c.merge());
            ps.setString(6, c.taskNumber() == null ? null : c.taskNumber().value());
            ps.setString(7, c.message());
            ps.setLong(8, c.addedLines());
            ps.setLong(9, c.deletedLines());
            ps.setLong(10, c.testAddedLines());
            ps.setString(11, c.repo() == null ? null : c.repo().value());
            ps.setObject(12, collectedAt);
            ps.setObject(13, email == null ? null : users.get(email));   // nullable FK на unified_user
            ps.setObject(14, kaitenCardId(c));                            // nullable
        });
        log.debug("Saved {} commits (native batch)", commits.size());
    }

    /** Числовой ID карточки Kaiten из task-номера, либо null если не парсится. */
    private static Long kaitenCardId(Commit c) {
        if (c.taskNumber() == null) return null;
        return c.taskNumber().asKaitenCardId().stream().boxed().findFirst().orElse(null);
    }

    @Override
    public List<Commit> findByAuthor(Email email, Period period,
                                     ru.x5.devpulse.domain.common.PageRequest page) {
        return jpa.findByAuthorAndPeriod(
                        email.value(),
                        period.fromAtStartOfDay(),
                        period.toAtEndOfDay(),
                        PageRequest.of(page.page(), page.size()))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void markSeen(Collection<CommitHash> hashes, java.time.LocalDateTime seenAt) {
        if (hashes == null || hashes.isEmpty()) return;
        List<String> values = hashes.stream().map(CommitHash::value).toList();
        jpa.markSeen(values, seenAt);
    }

    @Override
    @Transactional
    public int deleteZombies(RepoName repo, Period period, java.time.LocalDateTime seenBefore) {
        int deleted = jpa.deleteZombies(
                repo.value(),
                period.fromAtStartOfDay(),
                period.toAtEndOfDay(),
                seenBefore);
        if (deleted > 0) {
            log.info("Удалили {} rebase-зомби из commit_details (repo={})", deleted, repo.value());
        }
        return deleted;
    }

    @Override
    public List<HourlyBucket> aggregateHourly(Period period, Optional<Email> author, Optional<String> team) {
        String email = author.map(Email::value).orElse(null);
        String teamName = team.orElse(null);
        List<Object[]> rows = jpa.aggregateHourly(
                period.fromAtStartOfDay(), period.toAtEndOfDay(), email, teamName);

        List<HourlyBucket> buckets = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            // ((Number) …) — устойчиво к JDBC-типу (Integer/Long/BigInteger/BigDecimal).
            buckets.add(new HourlyBucket(
                    ((Number) r[0]).intValue(),
                    ((Number) r[1]).intValue(),
                    ((Number) r[2]).longValue(),
                    ((Number) r[3]).longValue()));
        }
        return buckets;
    }
}
