package ru.x5.markable.dev.analytics.adapter.persistence.commit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.x5.markable.dev.analytics.adapter.persistence.shared.PostgresContainerSupport;
import ru.x5.markable.dev.analytics.application.port.out.CommitRepository;
import ru.x5.markable.dev.analytics.domain.common.PageRequest;
import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.model.git.Commit;
import ru.x5.markable.dev.analytics.domain.model.git.CommitHash;
import ru.x5.markable.dev.analytics.domain.model.git.RepoName;
import ru.x5.markable.dev.analytics.domain.model.user.Email;

@SpringBootTest
@Testcontainers
@DisplayName("CommitRepositoryAdapter (TestContainers + PostgreSQL)")
class CommitRepositoryAdapterIT extends PostgresContainerSupport {

    private static final Email BORIS = new Email("boris-c@x5.ru");
    private static final RepoName CORE = new RepoName("xrg-core");
    private static final LocalDateTime JAN_5 = LocalDateTime.of(2026, 1, 5, 10, 0);
    private static final LocalDateTime JAN_6 = LocalDateTime.of(2026, 1, 6, 11, 0);
    private static final Period JANUARY = new Period(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

    @Autowired
    CommitRepository repo;

    @Test
    @DisplayName("saveAll сохраняет коммиты, findByAuthor возвращает их за период")
    void saveAllAndFindByAuthor() {
        Commit c1 = newCommit("a".repeat(40), BORIS, JAN_5);
        Commit c2 = newCommit("b".repeat(40), BORIS, JAN_6);

        repo.saveAll(List.of(c1, c2));
        List<Commit> found = repo.findByAuthor(BORIS, JANUARY, PageRequest.FIRST_PAGE);

        assertAll("сохранённые коммиты возвращаются по автору и периоду",
                () -> assertThat(found)
                        .as("должно быть сохранено и найдено 2 коммита Бориса")
                        .hasSize(2),
                () -> assertThat(found)
                        .as("оба коммита по хешам — c1 и c2 (порядок не важен)")
                        .extracting(c -> c.hash().value())
                        .containsExactlyInAnyOrder(c1.hash().value(), c2.hash().value()));
    }

    @Test
    @DisplayName("findExistingHashes возвращает только уже сохранённые хеши")
    void findExistingHashesReturnsOnlyKnown() {
        CommitHash known = new CommitHash("c".repeat(40));
        CommitHash unknown = new CommitHash("d".repeat(40));
        repo.saveAll(List.of(newCommit(known.value(), new Email("x@x5.ru"),
                LocalDateTime.of(2026, 2, 1, 9, 0))));

        Set<CommitHash> existing = repo.findExistingHashes(List.of(known, unknown));

        assertAll("результат findExistingHashes",
                () -> assertThat(existing)
                        .as("должен быть найден ровно один хеш — known")
                        .containsExactly(known),
                () -> assertThat(existing)
                        .as("unknown в результат не попадает")
                        .doesNotContain(unknown));
    }

    private static Commit newCommit(String hash, Email author, LocalDateTime when) {
        return new Commit(
                new CommitHash(hash), author, when,
                false, 10, 5, 0,
                "fix something", null, CORE);
    }
}
