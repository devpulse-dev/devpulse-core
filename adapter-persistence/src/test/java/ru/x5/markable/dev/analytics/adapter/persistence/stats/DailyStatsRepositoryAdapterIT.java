package ru.x5.markable.dev.analytics.adapter.persistence.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.x5.markable.dev.analytics.adapter.persistence.shared.PostgresContainerSupport;
import ru.x5.markable.dev.analytics.application.port.out.DailyStatsRepository;
import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.model.git.RepoName;
import ru.x5.markable.dev.analytics.domain.model.stats.DailyAuthorStats;
import ru.x5.markable.dev.analytics.domain.model.user.Email;

@SpringBootTest
@DisplayName("DailyStatsRepositoryAdapter (native UPSERT + TestContainers)")
class DailyStatsRepositoryAdapterIT extends PostgresContainerSupport {

    private static final Email BORIS = new Email("boris-s@x5.ru");
    private static final Email AUTHOR_A = new Email("a-s@x5.ru");
    private static final Email AUTHOR_B = new Email("b-s@x5.ru");
    private static final RepoName CORE = new RepoName("xrg-core");
    private static final LocalDate MAR_10 = LocalDate.of(2026, 3, 10);
    private static final LocalDate APR_1 = LocalDate.of(2026, 4, 1);

    @Autowired
    DailyStatsRepository repo;

    @Test
    @DisplayName("upsertAll: на повторе по тому же ключу (email,date,repo) обновляет, а не вставляет")
    void upsertInsertsThenUpdatesByCompositeKey() {
        DailyAuthorStats v1 = stats(BORIS, MAR_10, CORE, 3, 1, 100, 20, 0);
        DailyAuthorStats v2 = stats(BORIS, MAR_10, CORE, 5, 1, 200, 40, 10);

        repo.upsertAll(List.of(v1));
        repo.upsertAll(List.of(v2));

        List<DailyAuthorStats> result = repo.findByPeriod(new Period(MAR_10, MAR_10));
        List<DailyAuthorStats> mine = result.stream()
                .filter(s -> s.authorEmail().equals(BORIS) && s.repo().equals(CORE))
                .toList();

        assertAll("ON CONFLICT DO UPDATE по уникальному ключу",
                () -> assertThat(mine)
                        .as("после двух upsert'ов того же ключа должна остаться 1 запись")
                        .hasSize(1),
                () -> assertThat(mine.getFirst().commits())
                        .as("commits обновлены до 5 (вторая версия)")
                        .isEqualTo(5),
                () -> assertThat(mine.getFirst().addedLines())
                        .as("addedLines обновлены до 200")
                        .isEqualTo(200),
                () -> assertThat(mine.getFirst().testAddedLines())
                        .as("testAddedLines обновлены до 10")
                        .isEqualTo(10));
    }

    @Test
    @DisplayName("upsertAll: батч с разными ключами вставляет их все")
    void upsertHandlesBatchAcrossMultipleKeys() {
        repo.upsertAll(List.of(
                stats(AUTHOR_A, APR_1, CORE, 1, 0, 10, 0, 0),
                stats(AUTHOR_B, APR_1, CORE, 2, 0, 20, 0, 0)
        ));

        List<DailyAuthorStats> all = repo.findByPeriod(new Period(APR_1, APR_1));

        assertThat(all)
                .as("оба автора должны быть в результате выборки за %s/%s", APR_1, CORE)
                .filteredOn(s -> s.date().equals(APR_1) && s.repo().equals(CORE))
                .extracting(DailyAuthorStats::authorEmail)
                .contains(AUTHOR_A, AUTHOR_B);
    }

    private static DailyAuthorStats stats(Email email, LocalDate date, RepoName repo,
                                          long commits, long mergeCommits,
                                          long added, long deleted, long testAdded) {
        return new DailyAuthorStats(
                null, email, date, repo,
                commits, mergeCommits, added, deleted, testAdded,
                LocalDateTime.now(), null);
    }
}
