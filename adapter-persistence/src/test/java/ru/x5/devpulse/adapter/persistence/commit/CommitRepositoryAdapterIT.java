package ru.x5.devpulse.adapter.persistence.commit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.x5.devpulse.adapter.persistence.shared.PostgresContainerSupport;
import ru.x5.devpulse.application.port.out.CommitRepository;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
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

    @Autowired
    UnifiedUserRepository userRepo;

    @Test
    @DisplayName("saveAll сохраняет коммиты, findByAuthor возвращает их за период")
    void saveAllAndFindByAuthor() {
        Commit c1 = newCommit("a".repeat(40), BORIS, JAN_5);
        Commit c2 = newCommit("b".repeat(40), BORIS, JAN_6);

        repo.saveAll(List.of(c1, c2), Map.of());
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
                LocalDateTime.of(2026, 2, 1, 9, 0))), Map.of());

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
        ), Map.of());

        Period may = new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
        List<HourlyBucket> cells = repo.aggregateHourly(may, Optional.empty(), Optional.empty());

        HourlyBucket mon10 = cell(cells, 0, 10);
        HourlyBucket tue = cell(cells, 1, 14);
        assertAll("часовая агрегация",
                () -> assertThat(mon10.commits()).as("2 не-мердж коммита Пн 10:00").isEqualTo(2),
                () -> assertThat(mon10.addedLines()).as("10+10").isEqualTo(20),
                () -> assertThat(tue.commits()).isEqualTo(1),
                () -> assertThat(cells.stream().mapToLong(HourlyBucket::commits).sum())
                        .as("всего 3 — мердж исключён").isEqualTo(3),
                () -> assertThat(repo.aggregateHourly(may, Optional.of(new Email("nobody@x5.ru")), Optional.empty()))
                        .as("фильтр по чужому email → пусто").isEmpty());
    }

    @Test
    @DisplayName("aggregateHourly: фильтр team ограничивает участниками команды (членство по unified_user.team)")
    void aggregateHourlyFiltersByTeam() {
        Email platformDev = new Email("platform-dev@x5.ru");
        Email coreDev = new Email("core-dev@x5.ru");
        // unified_user join-ключ — email; членство берётся из колонки team.
        userRepo.findOrCreateAll(List.of(platformDev, coreDev));
        userRepo.updateTeam(platformDev, "Platform");
        userRepo.updateTeam(coreDev, "Core");

        LocalDateTime mon10 = LocalDateTime.of(2026, 5, 4, 10, 0);
        LocalDateTime tue14 = LocalDateTime.of(2026, 5, 5, 14, 0);
        repo.saveAll(List.of(
                newCommit("a1".repeat(20), platformDev, mon10),   // Platform → (0,10)
                newCommit("b2".repeat(20), coreDev, tue14)        // Core → (1,14)
        ), Map.of());

        Period may = new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        assertAll("фильтр по команде",
                () -> assertThat(repo.aggregateHourly(may, Optional.empty(), Optional.of("Platform")))
                        .as("только коммит участника Platform")
                        .extracting(HourlyBucket::weekday, HourlyBucket::hour)
                        .containsExactly(tuple(0, 10)),
                () -> assertThat(repo.aggregateHourly(may, Optional.empty(), Optional.of("Core")))
                        .as("только коммит участника Core")
                        .extracting(HourlyBucket::weekday, HourlyBucket::hour)
                        .containsExactly(tuple(1, 14)),
                () -> assertThat(repo.aggregateHourly(may, Optional.empty(), Optional.of("Ghost")))
                        .as("несуществующая команда → пусто").isEmpty());
    }

    @Test
    @DisplayName("mark-and-sweep: deleteZombies сносит непомеченные, markSeen защищает увиденные")
    void markAndSweepZombies() {
        RepoName sweepRepo = new RepoName("sweep-test");
        LocalDateTime when = LocalDateTime.of(2026, 3, 10, 9, 0);
        Period march = new Period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        CommitHash zombie = new CommitHash("e".repeat(40));
        CommitHash survivor = new CommitHash("f".repeat(40));

        // Оба коммита сохранены ДО runMark → их collected_at < runMark.
        repo.saveAll(List.of(
                commitInRepo(zombie.value(), when, sweepRepo),
                commitInRepo(survivor.value(), when, sweepRepo)), Map.of());

        LocalDateTime runMark = LocalDateTime.now();
        // survivor — «увиден в этом сборе»: collected_at поднимается до runMark.
        repo.markSeen(List.of(survivor), runMark);

        int deleted = repo.deleteZombies(sweepRepo, march, runMark);

        assertAll("mark-and-sweep",
                () -> assertThat(deleted).as("снесён ровно один зомби").isEqualTo(1),
                () -> assertThat(repo.findExistingHashes(List.of(zombie, survivor)))
                        .as("zombie удалён, survivor (markSeen) остался")
                        .containsExactly(survivor));
    }

    @Test
    @DisplayName("Scale (P2-3): native batch insert + mark-and-sweep на 3000 коммитах — корректно на объёме")
    void scaleInsertAndSweep() {
        // Размер, на котором старый zombie-cleanup материализовал бы все хеши в heap. Здесь память
        // O(batch): insert идёт чанками по 500 (native batchUpdate), sweep — set-разность в БД.
        RepoName scaleRepo = new RepoName("scale-test");
        LocalDateTime when = LocalDateTime.of(2026, 4, 15, 9, 0);
        Period april = new Period(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        int n = 3000;
        int seenCount = 2000; // первые 2000 «увидены», последние 1000 — зомби

        // 1. Один saveAll на 3000 коммитов → native batch insert (P0-1).
        List<Commit> commits = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            commits.add(commitInRepo(hashOf(i), when, scaleRepo));
        }
        repo.saveAll(commits, Map.of());
        assertThat(repo.findExistingHashes(hashes(0, n)))
                .as("все 3000 записаны batch-insert'ом").hasSize(n);

        // 2. Помечаем первые 2000 увиденными в текущем сборе, затем sweep (P0-2).
        LocalDateTime runMark = LocalDateTime.now();
        repo.markSeen(hashes(0, seenCount), runMark);
        int deleted = repo.deleteZombies(scaleRepo, april, runMark);

        assertAll("mark-and-sweep на объёме",
                () -> assertThat(deleted).as("снесены ровно непомеченные 1000").isEqualTo(n - seenCount),
                () -> assertThat(repo.findExistingHashes(hashes(0, n)))
                        .as("остались только 2000 помеченных").hasSize(seenCount));
    }

    /** 40-символьный hex-хеш из индекса — уникальный и валидный для CommitHash. */
    private static String hashOf(int i) {
        return String.format("%040x", i);
    }

    private static List<CommitHash> hashes(int fromInclusive, int toExclusive) {
        List<CommitHash> list = new ArrayList<>(toExclusive - fromInclusive);
        for (int i = fromInclusive; i < toExclusive; i++) {
            list.add(new CommitHash(hashOf(i)));
        }
        return list;
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

    private static Commit commitInRepo(String hash, LocalDateTime when, RepoName repo) {
        return new Commit(
                new CommitHash(hash), BORIS, when,
                false, 10, 5, 0,
                "fix something", null, repo);
    }
}
