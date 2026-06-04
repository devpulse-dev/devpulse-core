package ru.x5.devpulse.adapter.persistence.commit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.x5.devpulse.adapter.persistence.shared.PostgresContainerSupport;
import ru.x5.devpulse.application.port.out.CommitRepository;
import ru.x5.devpulse.domain.common.PageRequest;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.git.CommitHash;
import ru.x5.devpulse.domain.model.git.RepoName;
import ru.x5.devpulse.domain.model.stats.HourlyBucket;
import ru.x5.devpulse.domain.model.user.Email;

@SpringBootTest
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

    @Test
    @DisplayName("aggregateHourly: GROUP BY weekday×hour, мерджи исключены, weekday 0=Пн (ISODOW)")
    void aggregateHourlyGroupsByWeekdayAndHour() {
        // 2026-05-04 — понедельник (ISODOW=1 → weekday 0), 2026-05-05 — вторник (weekday 1).
        LocalDateTime mon10a = LocalDateTime.of(2026, 5, 4, 10, 0);
        LocalDateTime mon10b = LocalDateTime.of(2026, 5, 4, 10, 30);
        LocalDateTime tue14 = LocalDateTime.of(2026, 5, 5, 14, 0);
        LocalDateTime mon10merge = LocalDateTime.of(2026, 5, 4, 10, 45);

        repo.saveAll(List.of(
                newCommit("1".repeat(40), BORIS, mon10a),                  // (0,10) +10
                newCommit("2".repeat(40), BORIS, mon10b),                  // (0,10) +10
                newCommit("3".repeat(40), BORIS, tue14),                   // (1,14) +10
                mergeCommit("4".repeat(40), BORIS, mon10merge)             // мердж — НЕ считаем
        ));

        Period may = new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
        List<HourlyBucket> cells = repo.aggregateHourly(may, Optional.empty());

        HourlyBucket mon10 = cell(cells, 0, 10);
        HourlyBucket tue = cell(cells, 1, 14);
        assertAll("часовая агрегация",
                () -> assertThat(mon10.commits()).as("2 не-мердж коммита Пн 10:00").isEqualTo(2),
                () -> assertThat(mon10.addedLines()).as("10+10").isEqualTo(20),
                () -> assertThat(tue.commits()).isEqualTo(1),
                () -> assertThat(cells.stream().mapToLong(HourlyBucket::commits).sum())
                        .as("всего 3 — мердж исключён").isEqualTo(3),
                () -> assertThat(repo.aggregateHourly(may, Optional.of(new Email("nobody@x5.ru"))))
                        .as("фильтр по чужому email → пусто").isEmpty());
    }

    private static HourlyBucket cell(List<HourlyBucket> cells, int weekday, int hour) {
        return cells.stream()
                .filter(c -> c.weekday() == weekday && c.hour() == hour)
                .findFirst()
                .orElseThrow(() -> new AssertionError("нет ячейки (" + weekday + "," + hour + ")"));
    }

    private static Commit newCommit(String hash, Email author, LocalDateTime when) {
        return new Commit(
                new CommitHash(hash), author, when,
                false, 10, 5, 0,
                "fix something", null, CORE);
    }

    private static Commit mergeCommit(String hash, Email author, LocalDateTime when) {
        return new Commit(
                new CommitHash(hash), author, when,
                true, 10, 5, 0,
                "merge branch", null, CORE);
    }
}
