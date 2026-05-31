package ru.x5.devpulse.adapter.persistence.commit;

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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.x5.devpulse.application.port.out.CommitRepository;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
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

    private final CommitDetailsJpaRepository jpa;
    private final CommitEntityMapper mapper;
    private final UnifiedUserRepository unifiedUsers;

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
    public void saveAll(Collection<Commit> commits) {
        if (commits == null || commits.isEmpty()) return;

        // 1 batch find-or-create — все user_id одним SQL
        Set<Email> uniqueAuthors = new HashSet<>();
        commits.forEach(c -> uniqueAuthors.add(c.authorEmail()));
        Map<Email, Long> userByEmail = unifiedUsers.findOrCreateAll(uniqueAuthors);

        List<CommitDetailsEntity> entities = new ArrayList<>(commits.size());
        for (Commit c : commits) {
            entities.add(mapper.toEntity(c, userByEmail.get(c.authorEmail())));
        }
        jpa.saveAll(entities);
        log.debug("Saved {} commits", entities.size());
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
    public Set<CommitHash> findHashesByRepoAndPeriod(RepoName repo, Period period) {
        List<String> raw = jpa.findHashesByRepoAndPeriod(
                repo.value(),
                period.fromAtStartOfDay(),
                period.toAtEndOfDay());
        Set<CommitHash> result = new HashSet<>(raw.size());
        for (String h : raw) result.add(new CommitHash(h));
        return result;
    }

    @Override
    @Transactional
    public void deleteByHashes(Collection<CommitHash> hashes) {
        if (hashes == null || hashes.isEmpty()) return;
        List<String> values = hashes.stream().map(CommitHash::value).toList();
        int deleted = jpa.deleteByCommitHashes(values);
        log.info("Удалили {} rebase-зомби из commit_details", deleted);
    }

    @Override
    public List<HourlyBucket> aggregateHourly(Period period, Optional<Email> author) {
        String email = author.map(Email::value).orElse(null);
        List<Object[]> rows = jpa.aggregateHourly(
                period.fromAtStartOfDay(), period.toAtEndOfDay(), email);

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
