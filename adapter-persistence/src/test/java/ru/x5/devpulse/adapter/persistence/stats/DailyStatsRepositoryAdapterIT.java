package ru.x5.devpulse.adapter.persistence.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.x5.devpulse.adapter.persistence.shared.PostgresContainerSupport;
import ru.x5.devpulse.application.port.out.CommitRepository;
import ru.x5.devpulse.application.port.out.DailyStatsRepository;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.cohort.MonthlyAuthorActivity;
import ru.x5.devpulse.domain.common.TaskNumber;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.git.CommitHash;
import ru.x5.devpulse.domain.model.git.RepoName;
import ru.x5.devpulse.domain.model.user.Email;

@SpringBootTest
@DisplayName("DailyStatsRepositoryAdapter (recompute + TestContainers)")
class DailyStatsRepositoryAdapterIT extends PostgresContainerSupport {

    @Autowired DailyStatsRepository repo;
    @Autowired UnifiedUserRepository unifiedUserRepository;
    @Autowired CommitRepository commitRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    /**
     * Регрессионный тест на семантику {@code RECOMPUTE_SQL} — правильность маппинга user_id
     * для каждого email'а.
     *
     * <p>Цель: поймать любую регрессию вроде "subquery возвращает один и тот же user_id для
     * всех строк". См. post-mortem в REFACTORING.md про тонкость с alias в correlated subquery.</p>
     *
     * <p>Сценарий: два разных автора в unified_user + commit_details + recompute → у каждой
     * строки daily_author_stats user_id должен соответствовать своему email'у.</p>
     */
    @Test
    @DisplayName("recomputeFromCommits: user_id корректно мапится на unified_user по email")
    void recomputeAssignsCorrectUserId() {
        Email alice = new Email("alice-recompute@x5.ru");
        Email bob = new Email("bob-recompute@x5.ru");
        LocalDate d1 = LocalDate.of(2026, 7, 10);
        LocalDate d2 = LocalDate.of(2026, 7, 11);
        RepoName repoX = new RepoName("recompute-test");
        Period period = new Period(d1, d2);

        // 1. Создаём двух разных пользователей в unified_user.
        Map<Email, Long> userIds = unifiedUserRepository.findOrCreateAll(List.of(alice, bob));
        Long aliceId = userIds.get(alice);
        Long bobId = userIds.get(bob);
        assertThat(aliceId).as("alice должна быть создана").isNotNull();
        assertThat(bobId).as("bob должен быть создан").isNotNull();
        assertThat(aliceId).as("разные user_id у разных авторов").isNotEqualTo(bobId);

        // 2. Кладём по коммиту в commit_details на каждого.
        commitRepository.saveAll(List.of(
                commit("ab".repeat(20), alice, d1, repoX),
                commit("cd".repeat(20), bob, d2, repoX)), userIds);

        // 3. Recompute для обоих.
        repo.recomputeFromCommits(List.of(alice, bob), period);

        // 4. Читаем daily_author_stats напрямую — JpaRepository не вернёт user_id легко,
        //    а нам важен именно он.
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT email, user_id FROM daily_author_stats "
                        + "WHERE date BETWEEN ? AND ? AND repository_name = ? "
                        + "ORDER BY email",
                d1, d2, repoX.value());

        assertAll("user_id мапится 1:1 с email",
                () -> assertThat(rows).hasSize(2),
                () -> assertThat(rows)
                        .filteredOn(r -> alice.value().equals(r.get("email")))
                        .singleElement()
                        .extracting(r -> r.get("user_id"))
                        .as("alice -> aliceId")
                        .isEqualTo(aliceId),
                () -> assertThat(rows)
                        .filteredOn(r -> bob.value().equals(r.get("email")))
                        .singleElement()
                        .extracting(r -> r.get("user_id"))
                        .as("bob -> bobId")
                        .isEqualTo(bobId));
    }

    @Test
    @DisplayName("monthlyAuthorActivity: GROUP BY (email, месяц) суммирует коммиты по месяцам")
    void monthlyAggregatesByMonth() {
        Email dev = new Email("monthly@x5.ru");
        unifiedUserRepository.findOrCreateAll(List.of(dev));
        RepoName repoX = new RepoName("monthly-test");
        // Jan: 2 коммита (10, 11 янв), Feb: 1 коммит
        commitRepository.saveAll(List.of(
                commit("e1".repeat(20), dev, LocalDate.of(2026, 1, 10), repoX),
                commit("e2".repeat(20), dev, LocalDate.of(2026, 1, 11), repoX),
                commit("e3".repeat(20), dev, LocalDate.of(2026, 2, 5), repoX)), Map.of());
        repo.recomputeFromCommits(List.of(dev),
                new Period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28)));

        List<MonthlyAuthorActivity> mine = repo.monthlyAuthorActivity(
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null).stream()
                .filter(a -> a.email().value().equals("monthly@x5.ru")).toList();

        assertAll("помесячная агрегация",
                () -> assertThat(mine).extracting(a -> a.month().toString())
                        .containsExactlyInAnyOrder("2026-01", "2026-02"),
                () -> assertThat(byMonth(mine, "2026-01").nonMergeCommits()).as("Jan = 2").isEqualTo(2L),
                () -> assertThat(byMonth(mine, "2026-02").nonMergeCommits()).as("Feb = 1").isEqualTo(1L));
    }

    @Test
    @DisplayName("monthlyAuthorActivity: team-фильтр через подзапрос на unified_user")
    void monthlyTeamFilter() {
        Email dev = new Email("teamed@x5.ru");
        unifiedUserRepository.findOrCreateAll(List.of(dev));
        unifiedUserRepository.updateTeam(dev, "alpha");
        RepoName repoX = new RepoName("team-test");
        commitRepository.saveAll(List.of(
                commit("fa".repeat(20), dev, LocalDate.of(2026, 4, 5), repoX)), Map.of());
        repo.recomputeFromCommits(List.of(dev),
                new Period(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)));

        var apr = new Object() {
            boolean has(String team) {
                return repo.monthlyAuthorActivity(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), team)
                        .stream().anyMatch(a -> a.email().value().equals("teamed@x5.ru"));
            }
        };

        assertAll("team-фильтр",
                () -> assertThat(apr.has("alpha")).as("своя команда — есть").isTrue(),
                () -> assertThat(apr.has("beta")).as("чужая команда — нет").isFalse());
    }

    private static MonthlyAuthorActivity byMonth(List<MonthlyAuthorActivity> list, String ym) {
        return list.stream().filter(a -> a.month().toString().equals(ym)).findFirst().orElseThrow();
    }

    private static Commit commit(String hash, Email author, LocalDate date, RepoName repo) {
        return new Commit(
                new CommitHash(hash),
                author,
                date.atTime(12, 0),
                false,
                /*added*/ 10, /*deleted*/ 2, /*testAdded*/ 0,
                "TASK-1 fix",
                new TaskNumber("1"),
                repo);
    }
}
